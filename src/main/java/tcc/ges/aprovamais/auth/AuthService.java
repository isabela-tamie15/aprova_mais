package tcc.ges.aprovamais.auth;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.ConfiguracaoDoisFatoresResponse;
import tcc.ges.aprovamais.dto.LoginRequest;
import tcc.ges.aprovamais.dto.LoginResponse;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.repository.UsuarioRepository;
import tcc.ges.aprovamais.security.JwtService;
import tcc.ges.aprovamais.service.AuditoriaService;
import tcc.ges.aprovamais.service.BloqueioContaService;
import tcc.ges.aprovamais.service.DoisFatoresService;


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

    /*
       O noRollbackFor é essencial aqui. Quando o login falha, a gente lança
       BadCredentialsException, mas antes disso registra a falha no banco.
       Sem esse ajuste, o Spring reverteria a transação e a tentativa falha
       nunca seria gravada, o que quebraria todo o sistema de bloqueio
    */
    @Transactional(noRollbackFor = AuthenticationException.class)
    public LoginResponse login(LoginRequest requisicao, HttpServletRequest request) {
        String ip = request.getRemoteAddr();

        /*
           Busca o usuário pelo e-mail. Se não achar, já registra na auditoria
           e lança a mesma exceção genérica, sem entregar se o e-mail existe
        */
        Usuario usuario = usuarioRepository.findByEmail(requisicao.getEmail())
                .orElseThrow(() -> {
                    auditoriaService.registrarTentativa(
                            requisicao.getEmail(),
                            "LOGIN_FALHA",
                            "Usuário não encontrado",
                            ip
                    );
                    return new BadCredentialsException("Credenciais inválidas");
                });

        // Confere se a conta tá bloqueada antes de tentar autenticar
        bloqueioContaService.verificarBloqueio(usuario, "LOGIN_FALHA", ip);

        try {
            /*
               Aqui o Spring Security confere as credenciais de verdade.
               Se a senha tiver errada, cai no BadCredentials, se a conta
               tiver desativada, cai no Disabled
            */
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            requisicao.getEmail(),
                            requisicao.getSenha()
                    )
            );

        } catch (BadCredentialsException e) {
            // Senha errada, registra a falha e devolve a mesma mensagem genérica
            int tentativas = bloqueioContaService.registrarFalha(usuario);

            auditoriaService.registrar(
                    usuario,
                    "LOGIN_FALHA",
                    "Senha incorreta. Tentativa " + tentativas + " de " + BloqueioContaService.MAX_TENTATIVAS,
                    ip,
                    false
            );

            throw new BadCredentialsException("Credenciais inválidas");

        } catch (DisabledException e) {
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

        /*
           Senha ok. Agora verifica se o usuário já usa 2fa. Se usa, ainda
           não entrega o JWT, devolve um token temporário pra próxima etapa
        */
        if (Boolean.TRUE.equals(usuario.getDoisFatoresAtivo())) {
            String preAuthToken = doisFatoresService.gerarTokenPreAutenticacao(usuario);
            auditoriaService.registrar(usuario, "LOGIN_2FA_REQUERIDO",
                    "Aguardando verificação do segundo fator", ip, true);
            return LoginResponse.requer2FA(preAuthToken);
        }

        /*
           Caso diferente, o perfil exige 2fa mas a conta ainda não configurou.
           Nesse caso também não entrega o JWT, devolve um token temporário
           pra ele fazer a configuração antes de entrar
        */
        if (usuario.getPerfil().exigeDoisFatores()) {
            String preAuthToken = doisFatoresService.gerarTokenPreAutenticacao(usuario);
            auditoriaService.registrar(usuario, "LOGIN_CONFIGURACAO_2FA_REQUERIDA",
                    "Perfil exige 2FA e a conta ainda não o configurou", ip, true);
            return LoginResponse.requerConfiguracao2FA(preAuthToken);
        }

        // Sem 2fa pendente, login acaba aqui
        return concluirLogin(usuario, ip);
    }

    // Esse é o método que valida o código do 2fa e finaliza o login de quem já usa
    @Transactional(noRollbackFor = AuthenticationException.class)
    public LoginResponse verificarSegundoFator(String preAuthToken, String codigo, String ip) {

        Usuario usuario = doisFatoresService.buscarPorTokenPreAutenticacao(preAuthToken);

        // Se por algum motivo o 2fa foi desativado no meio do caminho, recusa
        if (!Boolean.TRUE.equals(usuario.getDoisFatoresAtivo())) {
            throw new BadCredentialsException("Credenciais inválidas");
        }

        validarCodigoDoLogin(usuario, usuario.getDoisFatoresSegredo(), codigo, ip);

        // Código ok, então invalida o token temporário pra não poder ser reusado
        doisFatoresService.invalidarPreAutenticacao(usuario);
        return concluirLogin(usuario, ip);
    }

    // Esse é o método que inicia a configuração obrigatória de 2fa, gerando o QR Code
    @Transactional(noRollbackFor = AuthenticationException.class)
    public ConfiguracaoDoisFatoresResponse iniciarConfiguracaoObrigatoria(String preAuthToken) {
        Usuario usuario = buscarUsuarioEmConfiguracaoObrigatoria(preAuthToken);

        // Renova o token pra dar tempo do usuário escanear o QR Code e digitar o código
        doisFatoresService.renovarPreAutenticacao(usuario);
        return doisFatoresService.gerarNovoSegredo(usuario);
    }

    // Esse é o método que confirma o código da configuração obrigatória e já loga o usuário
    @Transactional(noRollbackFor = AuthenticationException.class)
    public LoginResponse confirmarConfiguracaoObrigatoria(String preAuthToken, String codigo, String ip) {
        Usuario usuario = buscarUsuarioEmConfiguracaoObrigatoria(preAuthToken);

        validarCodigoDoLogin(usuario, usuario.getDoisFatoresSegredo(), codigo, ip);

        // Código certo, ativa o 2fa de vez e invalida o token temporário
        doisFatoresService.ativar(usuario, ip);
        doisFatoresService.invalidarPreAutenticacao(usuario);
        return concluirLogin(usuario, ip);
    }

    // Esse é o método que registra na auditoria quando alguém faz logout
    @Transactional(readOnly = true)
    public void registrarLogout(String email, String ip) {

        /*
           Só registra se o usuário existir. Se não existir, não faz nada,
           porque o logout pode ser chamado com um e-mail que já foi removido
        */
        usuarioRepository.findByEmail(email).ifPresent(usuario ->
                auditoriaService.registrar(usuario, "LOGOUT", "Logout realizado com sucesso", ip, true));
    }

    // Esse é o auxiliar que busca o usuário e confirma que ele tá no fluxo de configuração obrigatória
    private Usuario buscarUsuarioEmConfiguracaoObrigatoria(String preAuthToken) {
        Usuario usuario = doisFatoresService.buscarPorTokenPreAutenticacao(preAuthToken);

        // Esse fluxo só vale pra quem é obrigado a ter 2fa e ainda não ativou
        if (Boolean.TRUE.equals(usuario.getDoisFatoresAtivo()) || !usuario.getPerfil().exigeDoisFatores()) {
            throw new BadCredentialsException("Credenciais inválidas");
        }
        return usuario;
    }

    // Esse é o auxiliar que valida o código do 2fa, usado tanto no login normal quanto na configuração
    private void validarCodigoDoLogin(Usuario usuario, String segredo, String codigo, String ip) {
        bloqueioContaService.verificarBloqueio(usuario, "LOGIN_2FA_FALHA", ip);

        // Código errado, registra a falha e lança
        if (!doisFatoresService.codigoValido(segredo, codigo)) {
            int tentativas = bloqueioContaService.registrarFalha(usuario);

            auditoriaService.registrar(
                    usuario,
                    "LOGIN_2FA_FALHA",
                    "Código 2FA inválido. Tentativa " + tentativas + " de " + BloqueioContaService.MAX_TENTATIVAS,
                    ip,
                    false
            );

            throw new BadCredentialsException("Código de verificação inválido");
        }
    }

    // Esse é o auxiliar que fecha o login em si, gera o JWT e devolve a resposta final
    private LoginResponse concluirLogin(Usuario usuario, String ip) {

        // Login deu certo, então limpa qualquer falha anterior e desbloqueia a conta
        bloqueioContaService.limparFalhas(usuario);

        String token = jwtService.gerarToken(usuario.getEmail(), usuario.getPerfil().name());

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