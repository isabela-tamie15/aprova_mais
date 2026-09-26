package tcc.ges.aprovamais.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.ConfiguracaoDoisFatoresResponse;
import tcc.ges.aprovamais.dto.StatusDoisFatoresResponse;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.exception.CodigoDoisFatoresInvalidoException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class DoisFatoresService {

    private static final String EMISSOR = "Aprova+";

    private final GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator();

    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final BloqueioContaService bloqueioContaService;

    @Value("${jwt.pre-auth-expiration}")
    private long minutosPreAutenticacao;

    // ===== Token de pré-autenticação (fluxo de login) =====

    /**
     * Gera um token de pré-autenticação para o usuário que acabou de acertar a senha.
     * Retorna o valor original (enviado ao cliente); no banco fica apenas o hash.
     */
    public String gerarTokenPreAutenticacao(Usuario usuario) {
        String token = UUID.randomUUID().toString();
        usuario.setTokenPreAutenticacao(gerarHash(token));
        usuario.setExpiracaoPreAutenticacao(calcularExpiracao());
        usuarioRepository.save(usuario);
        return token;
    }

    /**
     * Busca o usuário dono de um token de pré-autenticação válido e não expirado.
     * Qualquer falha resulta em BadCredentialsException genérica.
     */
    public Usuario buscarPorTokenPreAutenticacao(String token) {
        if (token == null || token.isBlank()) {
            throw new BadCredentialsException("Credenciais inválidas");
        }

        Usuario usuario = usuarioRepository.findByTokenPreAutenticacao(gerarHash(token))
                .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas"));

        if (usuario.getExpiracaoPreAutenticacao() == null
                || OffsetDateTime.now(ZoneOffset.UTC).isAfter(usuario.getExpiracaoPreAutenticacao())) {
            throw new BadCredentialsException("Verificação expirada. Faça login novamente.");
        }

        // Conta desativada entre a senha e o segundo fator não pode concluir o login
        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new BadCredentialsException("Credenciais inválidas");
        }

        return usuario;
    }

    /**
     * Estende a validade do token (usado ao exibir o QR Code, para dar tempo de escanear).
     */
    public void renovarPreAutenticacao(Usuario usuario) {
        usuario.setExpiracaoPreAutenticacao(calcularExpiracao());
        usuarioRepository.save(usuario);
    }

    /**
     * Invalida o token após o uso: cada token vale para um único login.
     */
    public void invalidarPreAutenticacao(Usuario usuario) {
        usuario.setTokenPreAutenticacao(null);
        usuario.setExpiracaoPreAutenticacao(null);
        usuarioRepository.save(usuario);
    }

    // ===== TOTP =====

    public boolean codigoValido(String segredo, String codigo) {
        if (segredo == null || codigo == null || !codigo.matches("\\d{6}")) {
            return false;
        }
        return googleAuthenticator.authorize(segredo, Integer.parseInt(codigo));
    }

    /**
     * Gera um novo segredo (ainda não ativo) e devolve os dados para o QR Code.
     * O segredo é gravado cifrado (AesEncryptor na entidade Usuario).
     */
    public ConfiguracaoDoisFatoresResponse gerarNovoSegredo(Usuario usuario) {
        GoogleAuthenticatorKey chave = googleAuthenticator.createCredentials();
        usuario.setDoisFatoresSegredo(chave.getKey());
        usuarioRepository.save(usuario);

        // getOtpAuthTotpURL apenas monta a URI otpauth://; o QR Code é gerado no navegador,
        // sem enviar o segredo a serviços externos (por isso não se usa getOtpAuthURL).
        String uri = GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL(EMISSOR, usuario.getEmail(), chave);
        return new ConfiguracaoDoisFatoresResponse(uri, chave.getKey());
    }

    public void ativar(Usuario usuario, String ipOrigem) {
        usuario.setDoisFatoresAtivo(true);
        usuarioRepository.save(usuario);

        auditoriaService.registrar(usuario, "DOIS_FATORES_ATIVADO",
                "Autenticação de dois fatores ativada", ipOrigem, true);
    }

    // ===== Gestão pela conta (usuário autenticado) =====

    @Transactional(readOnly = true)
    public StatusDoisFatoresResponse consultarStatus(String email) {
        Usuario usuario = buscarPorEmail(email);
        return new StatusDoisFatoresResponse(
                Boolean.TRUE.equals(usuario.getDoisFatoresAtivo()),
                usuario.getPerfil().exigeDoisFatores()
        );
    }

    @Transactional
    public ConfiguracaoDoisFatoresResponse configurar(String email) {
        Usuario usuario = buscarPorEmail(email);

        if (Boolean.TRUE.equals(usuario.getDoisFatoresAtivo())) {
            throw new IllegalStateException("A autenticação de dois fatores já está ativa.");
        }

        return gerarNovoSegredo(usuario);
    }

    @Transactional(noRollbackFor = {CodigoDoisFatoresInvalidoException.class, AuthenticationException.class})
    public void ativarPelaConta(String email, String codigo, String ipOrigem) {
        Usuario usuario = buscarPorEmail(email);

        if (Boolean.TRUE.equals(usuario.getDoisFatoresAtivo())) {
            throw new IllegalStateException("A autenticação de dois fatores já está ativa.");
        }
        if (usuario.getDoisFatoresSegredo() == null) {
            throw new IllegalStateException("Gere o QR Code antes de ativar a autenticação de dois fatores.");
        }

        validarCodigoDaConta(usuario, codigo, ipOrigem);
        ativar(usuario, ipOrigem);
    }

    @Transactional(noRollbackFor = {CodigoDoisFatoresInvalidoException.class, AuthenticationException.class})
    public void desativar(String email, String codigo, String ipOrigem) {
        Usuario usuario = buscarPorEmail(email);

        if (usuario.getPerfil().exigeDoisFatores()) {
            throw new IllegalStateException("A autenticação de dois fatores é obrigatória para o seu perfil.");
        }
        if (!Boolean.TRUE.equals(usuario.getDoisFatoresAtivo())) {
            throw new IllegalStateException("A autenticação de dois fatores não está ativa.");
        }

        // Exige código válido: impede que alguém com a sessão aberta desative o 2FA sozinho
        validarCodigoDaConta(usuario, codigo, ipOrigem);

        usuario.setDoisFatoresAtivo(false);
        usuario.setDoisFatoresSegredo(null);
        usuarioRepository.save(usuario);

        auditoriaService.registrar(usuario, "DOIS_FATORES_DESATIVADO",
                "Autenticação de dois fatores desativada", ipOrigem, true);
    }

    // ===== Auxiliares =====

    /**
     * Valida o código compartilhando o limite de tentativas do login,
     * para impedir adivinhação do código por quem tem a sessão aberta.
     */
    private void validarCodigoDaConta(Usuario usuario, String codigo, String ipOrigem) {
        bloqueioContaService.verificarBloqueio(usuario, "DOIS_FATORES_FALHA", ipOrigem);

        if (!codigoValido(usuario.getDoisFatoresSegredo(), codigo)) {
            int tentativas = bloqueioContaService.registrarFalha(usuario);
            auditoriaService.registrar(usuario, "DOIS_FATORES_FALHA",
                    "Código inválido. Tentativa " + tentativas + " de " + BloqueioContaService.MAX_TENTATIVAS,
                    ipOrigem, false);
            throw new CodigoDoisFatoresInvalidoException();
        }

        bloqueioContaService.limparFalhas(usuario);
    }

    private Usuario buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    private OffsetDateTime calcularExpiracao() {
        return OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(minutosPreAutenticacao);
    }

    private String gerarHash(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }
}
