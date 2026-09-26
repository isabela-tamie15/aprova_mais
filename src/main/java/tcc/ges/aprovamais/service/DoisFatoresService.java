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

// Esse service cuida de tudo relacionado ao 2fa, tanto o token de pré-autenticação quanto o TOTP em si
@Service
@RequiredArgsConstructor
public class DoisFatoresService {

    private static final String EMISSOR = "Aprova+";

    private final GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator();

    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final BloqueioContaService bloqueioContaService;

    // Tempo em minutos que o token de pré-autenticação fica válido, vem do application.yml
    @Value("${jwt.pre-auth-expiration}")
    private long minutosPreAutenticacao;

    // ===== Token de pré-autenticação (fluxo de login) =====

    /*
       Gera o token de pré-autenticação pra quem acabou de acertar a senha.
       O valor original vai pro cliente, mas o que fica salvo no banco é só
       o hash. Assim, se o banco vazar, ninguém consegue usar os tokens que
       estão lá dentro
    */
    public String gerarTokenPreAutenticacao(Usuario usuario) {
        String token = UUID.randomUUID().toString();
        usuario.setTokenPreAutenticacao(gerarHash(token));
        usuario.setExpiracaoPreAutenticacao(calcularExpiracao());
        usuarioRepository.save(usuario);
        return token;
    }

    /*
       Busca o usuário dono do token de pré-autenticação. Faz várias checagens
       em sequência: token existe, não expirou e a conta ainda tá ativa. Se
       qualquer uma falhar, lança a mesma exceção genérica, sem dizer qual
       foi o problema, pra não dar pista pra quem tá tentando adivinhar
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

    // Estende a validade do token, usado ao exibir o QR Code pra dar tempo do usuário escanear
    public void renovarPreAutenticacao(Usuario usuario) {
        usuario.setExpiracaoPreAutenticacao(calcularExpiracao());
        usuarioRepository.save(usuario);
    }

    // Invalida o token depois do uso, cada token vale pra um único login
    public void invalidarPreAutenticacao(Usuario usuario) {
        usuario.setTokenPreAutenticacao(null);
        usuario.setExpiracaoPreAutenticacao(null);
        usuarioRepository.save(usuario);
    }

    // ===== TOTP =====

    // Confere se o código de 6 dígitos bate com o segredo do usuário
    public boolean codigoValido(String segredo, String codigo) {
        if (segredo == null || codigo == null || !codigo.matches("\\d{6}")) {
            return false;
        }
        return googleAuthenticator.authorize(segredo, Integer.parseInt(codigo));
    }

    /*
       Gera um segredo novo pro usuário, ainda não ativo. O segredo em si
       fica cifrado no banco, o AesEncryptor cuida disso quando salva.
       Devolve a URI que o frontend usa pra montar o QR Code
    */
    public ConfiguracaoDoisFatoresResponse gerarNovoSegredo(Usuario usuario) {
        GoogleAuthenticatorKey chave = googleAuthenticator.createCredentials();
        usuario.setDoisFatoresSegredo(chave.getKey());
        usuarioRepository.save(usuario);

        /*
           Usa o getOtpAuthTotpURL em vez do getOtpAuthURL porque o primeiro
           só monta a URI no formato otpauth:// e o QR Code é gerado no
           navegador. O segundo geraria um QR Code chamando um serviço
           externo, o que mandaria o segredo do usuário pra fora
        */
        String uri = GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL(EMISSOR, usuario.getEmail(), chave);
        return new ConfiguracaoDoisFatoresResponse(uri, chave.getKey());
    }

    // Ativa o 2fa de vez e registra na auditoria
    public void ativar(Usuario usuario, String ipOrigem) {
        usuario.setDoisFatoresAtivo(true);
        usuarioRepository.save(usuario);

        auditoriaService.registrar(usuario, "DOIS_FATORES_ATIVADO",
                "Autenticação de dois fatores ativada", ipOrigem, true);
    }

    // ===== Gestão pela conta (usuário autenticado) =====

    // Diz se o usuário já tem 2fa ativado e se o perfil dele exige
    @Transactional(readOnly = true)
    public StatusDoisFatoresResponse consultarStatus(String email) {
        Usuario usuario = buscarPorEmail(email);
        return new StatusDoisFatoresResponse(
                Boolean.TRUE.equals(usuario.getDoisFatoresAtivo()),
                usuario.getPerfil().exigeDoisFatores()
        );
    }

    // Gera um segredo novo pra configurar o 2fa, usado quando o usuário tá ativando pela conta
    @Transactional
    public ConfiguracaoDoisFatoresResponse configurar(String email) {
        Usuario usuario = buscarPorEmail(email);

        if (Boolean.TRUE.equals(usuario.getDoisFatoresAtivo())) {
            throw new IllegalStateException("A autenticação de dois fatores já está ativa.");
        }

        return gerarNovoSegredo(usuario);
    }

    /*
       Ativa o 2fa validando um código primeiro pra confirmar que o usuário
       conseguiu configurar o app autenticador. O noRollbackFor é porque, se
       o código tiver errado, a gente já registrou a falha na auditoria e
       quer que isso persista mesmo com a exceção
    */
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

    // Desativa o 2fa, mas exige um código válido antes pra confirmar
    @Transactional(noRollbackFor = {CodigoDoisFatoresInvalidoException.class, AuthenticationException.class})
    public void desativar(String email, String codigo, String ipOrigem) {
        Usuario usuario = buscarPorEmail(email);

        if (usuario.getPerfil().exigeDoisFatores()) {
            throw new IllegalStateException("A autenticação de dois fatores é obrigatória para o seu perfil.");
        }
        if (!Boolean.TRUE.equals(usuario.getDoisFatoresAtivo())) {
            throw new IllegalStateException("A autenticação de dois fatores não está ativa.");
        }

        /*
           Exige o código mesmo com a sessão aberta. Assim, se alguém pegar
           o computador do usuário já logado, não consegue simplesmente
           desativar o 2fa da vítima
        */
        validarCodigoDaConta(usuario, codigo, ipOrigem);

        usuario.setDoisFatoresAtivo(false);
        usuario.setDoisFatoresSegredo(null);
        usuarioRepository.save(usuario);

        auditoriaService.registrar(usuario, "DOIS_FATORES_DESATIVADO",
                "Autenticação de dois fatores desativada", ipOrigem, true);
    }

    // ===== Auxiliares =====

    /*
       Valida o código do 2fa compartilhando o mesmo limite de tentativas do
       login. Isso impede que alguém com a sessão aberta fique testando
       códigos até acertar, porque depois de 5 erros a conta bloqueia
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

    // Busca o usuário pelo e-mail, usado nos métodos que recebem o e-mail do token
    private Usuario buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    // Calcula quando o token de pré-autenticação vai expirar
    private OffsetDateTime calcularExpiracao() {
        return OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(minutosPreAutenticacao);
    }

    /*
       Gera o hash SHA-256 do token antes de salvar no banco. A ideia é
       a mesma de senha, nunca guardar o valor original, só o hash. Assim
       mesmo que o banco vaze, o atacante não consegue usar os tokens
    */
    private String gerarHash(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }
}
