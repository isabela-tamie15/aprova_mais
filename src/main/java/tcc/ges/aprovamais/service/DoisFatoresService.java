package tcc.ges.aprovamais.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.ConfiguracaoDoisFatoresResponse;
import tcc.ges.aprovamais.dto.StatusDoisFatoresResponse;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.entity.enums.ParametroSistema;
import tcc.ges.aprovamais.exception.CodigoDoisFatoresInvalidoException;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.UsuarioRepository;
import tcc.ges.aprovamais.security.HashToken;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// Token de pré-autenticação e códigos TOTP da verificação em duas etapas
@Service
@RequiredArgsConstructor
public class DoisFatoresService {

    private static final String EMISSOR = "Aprova+";

    private final GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator();

    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final BloqueioContaService bloqueioContaService;

    // ===== Token de pré-autenticação (fluxo de login) =====

    /*
       Gera o token de pré-autenticação para quem acertou a senha. O cliente recebe o valor original
       e o banco guarda só o hash, então um vazamento do banco não expõe tokens válidos
    */
    public String gerarTokenPreAutenticacao(Usuario usuario) {
        String token = UUID.randomUUID().toString();
        usuario.setTokenPreAutenticacao(HashToken.sha256(token));
        usuario.setExpiracaoPreAutenticacao(calcularExpiracao());
        usuarioRepository.save(usuario);
        return token;
    }

    /*
       Busca o dono do token de pré-autenticação, conferindo se o token existe, não expirou e se a
       conta continua ativa. Qualquer falha dá a mesma exceção, sem dizer qual foi o problema
    */
    public Usuario buscarPorTokenPreAutenticacao(String token) {
        if (token == null || token.isBlank()) {
            throw new BadCredentialsException("Credenciais inválidas");
        }

        Usuario usuario = usuarioRepository.findByTokenPreAutenticacao(HashToken.sha256(token))
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

    // Estende a validade do token, usado ao exibir o QR Code para dar tempo do usuário escanear
    public void renovarPreAutenticacao(Usuario usuario) {
        usuario.setExpiracaoPreAutenticacao(calcularExpiracao());
        usuarioRepository.save(usuario);
    }

    // Invalida o token depois do uso, cada token vale para um único login
    public void invalidarPreAutenticacao(Usuario usuario) {
        usuario.setTokenPreAutenticacao(null);
        usuario.setExpiracaoPreAutenticacao(null);
        usuarioRepository.save(usuario);
    }

    // O TOTP troca de código a cada 30 segundos, e cada intervalo é um passo
    private static final long MILISSEGUNDOS_POR_PASSO = 30_000L;

    /*
       Confere o código de 6 dígitos e se ele ainda não foi usado. Aceita o passo atual e um para
       cada lado, por causa de relógio adiantado ou atrasado no celular
    */
    public boolean codigoValido(Usuario usuario, String codigo) {
        String segredo = usuario.getDoisFatoresSegredo();
        if (segredo == null || codigo == null || !codigo.matches("\\d{6}")) {
            return false;
        }
        int codigoDigitado = Integer.parseInt(codigo);
        long passoAtual = System.currentTimeMillis() / MILISSEGUNDOS_POR_PASSO;

        for (long passo = passoAtual - 1; passo <= passoAtual + 1; passo++) {
            if (googleAuthenticator.getTotpPassword(segredo, passo * MILISSEGUNDOS_POR_PASSO) == codigoDigitado) {
                Long ultimoPassoUsado = usuario.getUltimoPassoTotp();
                if (ultimoPassoUsado != null && passo <= ultimoPassoUsado) {
                    return false;
                }
                usuario.setUltimoPassoTotp(passo);
                usuarioRepository.save(usuario);
                return true;
            }
        }
        return false;
    }

    /*
       Gera um segredo novo, ainda não ativo, que o AesEncryptor cifra ao salvar. Devolve a URI
       usada pela tela para montar o QR Code
    */
    public ConfiguracaoDoisFatoresResponse gerarNovoSegredo(Usuario usuario) {
        GoogleAuthenticatorKey chave = googleAuthenticator.createCredentials();
        usuario.setDoisFatoresSegredo(chave.getKey());
        usuarioRepository.save(usuario);

        /*
           O getOtpAuthTotpURL só monta a URI otpauth://, e o QR Code é gerado no navegador.
           O getOtpAuthURL chamaria um serviço externo e mandaria o segredo para fora
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


    // Diz se o usuário já tem 2fa ativado e se o perfil dele exige
    @Transactional(readOnly = true)
    public StatusDoisFatoresResponse consultarStatus(String email) {
        Usuario usuario = buscarPorEmail(email);
        return new StatusDoisFatoresResponse(
                Boolean.TRUE.equals(usuario.getDoisFatoresAtivo()),
                usuario.getPerfil().exigeDoisFatores()
        );
    }

    // Gera um segredo novo para quem está ativando o 2FA pela conta
    @Transactional
    public ConfiguracaoDoisFatoresResponse configurar(String email) {
        Usuario usuario = buscarPorEmail(email);

        if (Boolean.TRUE.equals(usuario.getDoisFatoresAtivo())) {
            throw new RegraNegocioException("A autenticação de dois fatores já está ativa.");
        }

        return gerarNovoSegredo(usuario);
    }

    /*
       Ativa o 2FA depois de validar um código, para confirmar que o app autenticador foi configurado.
       O noRollbackFor mantém gravada a falha registrada quando o código está errado
    */
    @Transactional(noRollbackFor = {CodigoDoisFatoresInvalidoException.class, AuthenticationException.class})
    public void ativarPelaConta(String email, String codigo, String ipOrigem) {
        Usuario usuario = buscarPorEmail(email);

        if (Boolean.TRUE.equals(usuario.getDoisFatoresAtivo())) {
            throw new RegraNegocioException("A autenticação de dois fatores já está ativa.");
        }
        if (usuario.getDoisFatoresSegredo() == null) {
            throw new RegraNegocioException("Gere o QR Code antes de ativar a autenticação de dois fatores.");
        }

        validarCodigoDaConta(usuario, codigo, ipOrigem);
        ativar(usuario, ipOrigem);
    }

    // Desativa o 2fa, exigindo um código válido. Devolve o usuário para o controller gerar o token novo
    @Transactional(noRollbackFor = {CodigoDoisFatoresInvalidoException.class, AuthenticationException.class})
    public Usuario desativar(String email, String codigo, String ipOrigem) {
        Usuario usuario = buscarPorEmail(email);

        if (usuario.getPerfil().exigeDoisFatores()) {
            throw new RegraNegocioException("A autenticação de dois fatores é obrigatória para o seu perfil.");
        }
        if (!Boolean.TRUE.equals(usuario.getDoisFatoresAtivo())) {
            throw new RegraNegocioException("A autenticação de dois fatores não está ativa.");
        }

        // Exige o código mesmo com a sessão aberta, para quem pegar o computador logado não desativar o 2FA
        validarCodigoDaConta(usuario, codigo, ipOrigem);

        usuario.setDoisFatoresAtivo(false);
        usuario.setDoisFatoresSegredo(null);

        // Encerra as outras sessões da conta
        usuario.encerrarSessoes();
        usuarioRepository.save(usuario);

        auditoriaService.registrar(usuario, "DOIS_FATORES_DESATIVADO",
                "Autenticação de dois fatores desativada", ipOrigem, true);
        return usuario;
    }

    // Valida o código do 2FA com o mesmo limite de tentativas do login. Depois de 5 erros a conta bloqueia
    private void validarCodigoDaConta(Usuario usuario, String codigo, String ipOrigem) {
        bloqueioContaService.verificarBloqueio(usuario, "DOIS_FATORES_FALHA", ipOrigem);

        if (!codigoValido(usuario, codigo)) {
            int tentativas = bloqueioContaService.registrarFalha(usuario);
            auditoriaService.registrar(usuario, "DOIS_FATORES_FALHA",
                    "Código inválido. Tentativa " + tentativas + " de " + BloqueioContaService.maximoTentativas(),
                    ipOrigem, false);
            throw new CodigoDoisFatoresInvalidoException();
        }

        bloqueioContaService.limparFalhas(usuario);
    }

    private Usuario buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    // Calcula quando o token de pré-autenticação vai expirar
    private OffsetDateTime calcularExpiracao() {
        return OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(ParametrosVigentes.valor(ParametroSistema.MINUTOS_PRE_AUTENTICACAO));
    }

}
