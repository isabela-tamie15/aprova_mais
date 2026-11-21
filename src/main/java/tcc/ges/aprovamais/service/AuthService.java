package tcc.ges.aprovamais.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.ConfiguracaoDoisFatoresResponse;
import tcc.ges.aprovamais.dto.LoginRequest;
import tcc.ges.aprovamais.dto.LoginResponse;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.repository.UsuarioRepository;
import tcc.ges.aprovamais.security.JwtService;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final BloqueioContaService bloqueioContaService;
    private final DoisFatoresService doisFatoresService;
    private final PasswordEncoder passwordEncoder;

    // Hash de uma senha aleatória, conferido quando o e-mail não existe para o tempo de resposta ser o mesmo de uma conta real
    private static final String HASH_FICTICIO = "$2a$10$go3.2kJnPr9K04ToKTI2M.6pDFe8o/AmB7zWJoEXGCpHwF4vuF4ku";

    // O noRollbackFor mantém gravada a falha de login, que conta para o bloqueio da conta
    @Transactional(noRollbackFor = AuthenticationException.class)
    public LoginResponse login(LoginRequest requisicao, HttpServletRequest request) {
        String ip = request.getRemoteAddr();

        // E-mail sem cadastro fica na auditoria e recebe a mesma resposta de senha errada
        Usuario usuario = usuarioRepository.findByEmail(requisicao.getEmail())
                .orElseThrow(() -> {
                    passwordEncoder.matches(requisicao.getSenha(), HASH_FICTICIO);
                    auditoriaService.registrarTentativa(
                            requisicao.getEmail(),
                            "LOGIN_FALHA",
                            "Usuário não encontrado",
                            ip
                    );
                    return new BadCredentialsException("Credenciais inválidas");
                });

        // Conta bloqueada recebe a mesma resposta da senha errada, para não revelar que o e-mail existe
        try {
            bloqueioContaService.verificarBloqueio(usuario, "LOGIN_FALHA", ip);
        } catch (LockedException excecao) {
            passwordEncoder.matches(requisicao.getSenha(), HASH_FICTICIO);
            throw new BadCredentialsException("Credenciais inválidas");
        }

        try {
            // Confere as credenciais. Senha errada cai no BadCredentials e conta desativada no Disabled
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            requisicao.getEmail(),
                            requisicao.getSenha()
                    )
            );

        } catch (BadCredentialsException excecao) {
            // Senha errada, registra a falha e devolve a mesma mensagem genérica
            int tentativas = bloqueioContaService.registrarFalha(usuario);

            auditoriaService.registrar(
                    usuario,
                    "LOGIN_FALHA",
                    "Senha incorreta. Tentativa " + tentativas + " de " + BloqueioContaService.maximoTentativas(),
                    ip,
                    false
            );

            throw new BadCredentialsException("Credenciais inválidas");

        } catch (DisabledException excecao) {
            // Conta desativada, registra e devolve genérica também
            auditoriaService.registrar(
                    usuario,
                    "LOGIN_FALHA",
                    "Tentativa de login em conta inativa",
                    ip,
                    false
            );

            throw new BadCredentialsException("Credenciais inválidas");
        }

        // Com o 2FA ativo, o JWT ainda não é entregue, e sim um token temporário para a próxima etapa
        if (Boolean.TRUE.equals(usuario.getDoisFatoresAtivo())) {
            String preAuthToken = doisFatoresService.gerarTokenPreAutenticacao(usuario);
            auditoriaService.registrar(usuario, "LOGIN_2FA_REQUERIDO",
                    "Aguardando verificação do segundo fator", ip, true);
            return LoginResponse.requer2FA(preAuthToken);
        }

        // Perfil que exige 2FA e ainda não configurou recebe um token temporário para fazer a configuração
        if (usuario.getPerfil().exigeDoisFatores()) {
            String preAuthToken = doisFatoresService.gerarTokenPreAutenticacao(usuario);
            auditoriaService.registrar(usuario, "LOGIN_CONFIGURACAO_2FA_REQUERIDA",
                    "Perfil exige 2FA e a conta ainda não o configurou", ip, true);
            return LoginResponse.requerConfiguracao2FA(preAuthToken);
        }

        // Sem 2fa pendente, login acaba aqui
        return concluirLogin(usuario, ip);
    }

    // Valida o código do 2FA e conclui o login de quem já usa
    @Transactional(noRollbackFor = AuthenticationException.class)
    public LoginResponse verificarSegundoFator(String preAuthToken, String codigo, String ip) {

        Usuario usuario = doisFatoresService.buscarPorTokenPreAutenticacao(preAuthToken);

        // Se por algum motivo o 2fa foi desativado no meio do caminho, recusa
        if (!Boolean.TRUE.equals(usuario.getDoisFatoresAtivo())) {
            throw new BadCredentialsException("Credenciais inválidas");
        }

        validarCodigoDoLogin(usuario, codigo, ip);

        // Invalida o token temporário para ele não ser reusado
        doisFatoresService.invalidarPreAutenticacao(usuario);
        return concluirLogin(usuario, ip);
    }

    // Inicia a configuração obrigatória do 2FA, gerando o QR Code
    @Transactional(noRollbackFor = AuthenticationException.class)
    public ConfiguracaoDoisFatoresResponse iniciarConfiguracaoObrigatoria(String preAuthToken) {
        Usuario usuario = buscarUsuarioEmConfiguracaoObrigatoria(preAuthToken);

        // Renova o token para dar tempo do usuário escanear o QR Code e digitar o código
        doisFatoresService.renovarPreAutenticacao(usuario);
        return doisFatoresService.gerarNovoSegredo(usuario);
    }

    // Confirma o código da configuração obrigatória e já conclui o login
    @Transactional(noRollbackFor = AuthenticationException.class)
    public LoginResponse confirmarConfiguracaoObrigatoria(String preAuthToken, String codigo, String ip) {
        Usuario usuario = buscarUsuarioEmConfiguracaoObrigatoria(preAuthToken);

        validarCodigoDoLogin(usuario, codigo, ip);

        // Código certo, ativa o 2fa de vez e invalida o token temporário
        doisFatoresService.ativar(usuario, ip);
        doisFatoresService.invalidarPreAutenticacao(usuario);
        return concluirLogin(usuario, ip);
    }

    // Registra o logout na auditoria
    @Transactional
    public void registrarLogout(String email, String ip) {

        // O logout pode chegar com um e-mail que já não existe, e aí não há o que registrar
        usuarioRepository.findByEmail(email).ifPresent(usuario ->
                auditoriaService.registrar(usuario, "LOGOUT", "Logout realizado com sucesso", ip, true));
    }

    // Busca o usuário e confirma que ele está no fluxo de configuração obrigatória
    private Usuario buscarUsuarioEmConfiguracaoObrigatoria(String preAuthToken) {
        Usuario usuario = doisFatoresService.buscarPorTokenPreAutenticacao(preAuthToken);

        // Esse fluxo só vale para quem é obrigado a ter 2fa e ainda não ativou
        if (Boolean.TRUE.equals(usuario.getDoisFatoresAtivo()) || !usuario.getPerfil().exigeDoisFatores()) {
            throw new BadCredentialsException("Credenciais inválidas");
        }
        return usuario;
    }

    // Valida o código do 2FA no login e na configuração obrigatória
    private void validarCodigoDoLogin(Usuario usuario, String codigo, String ip) {
        bloqueioContaService.verificarBloqueio(usuario, "LOGIN_2FA_FALHA", ip);

        // Código errado, registra a falha e lança
        if (!doisFatoresService.codigoValido(usuario, codigo)) {
            int tentativas = bloqueioContaService.registrarFalha(usuario);

            auditoriaService.registrar(
                    usuario,
                    "LOGIN_2FA_FALHA",
                    "Código 2FA inválido. Tentativa " + tentativas + " de " + BloqueioContaService.maximoTentativas(),
                    ip,
                    false
            );

            throw new BadCredentialsException("Código de verificação inválido");
        }
    }

    // Conclui o login, gerando o JWT e a resposta final
    private LoginResponse concluirLogin(Usuario usuario, String ip) {

        // Login deu certo, então limpa qualquer falha anterior e desbloqueia a conta
        bloqueioContaService.limparFalhas(usuario);

        String token = jwtService.gerarToken(usuario.getEmail(), usuario.getPerfil().name(),
                usuario.versaoSessaoAtual());

        auditoriaService.registrar(
                usuario,
                "LOGIN_SUCESSO",
                "Login realizado com sucesso",
                ip,
                true
        );

        log.info("[AUTH] Login bem-sucedido: {}", usuario.getEmail());

        return LoginResponse.builder()
                .token(token)
                .nome(usuario.getNome())
                .email(usuario.getEmail())
                .perfil(usuario.getPerfil())
                .requer2FA(false)
                .build();
    }
}