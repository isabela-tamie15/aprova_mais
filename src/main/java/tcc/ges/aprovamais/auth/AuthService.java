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


    @Transactional(noRollbackFor = AuthenticationException.class)
    public LoginResponse login(LoginRequest requisicao, HttpServletRequest request) {
        String ip = request.getRemoteAddr();


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

        bloqueioContaService.verificarBloqueio(usuario, "LOGIN_FALHA", ip);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            requisicao.getEmail(),
                            requisicao.getSenha()
                    )
            );

        } catch (BadCredentialsException e) {
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

            auditoriaService.registrar(
                    usuario,
                    "LOGIN_FALHA",
                    "Tentativa de login em conta inativa",
                    ip,
                    false
            );

            throw new BadCredentialsException("Credenciais inválidas");
        }


        if (Boolean.TRUE.equals(usuario.getDoisFatoresAtivo())) {
            String preAuthToken = doisFatoresService.gerarTokenPreAutenticacao(usuario);
            auditoriaService.registrar(usuario, "LOGIN_2FA_REQUERIDO",
                    "Aguardando verificação do segundo fator", ip, true);
            return LoginResponse.requer2FA(preAuthToken);
        }

        if (usuario.getPerfil().exigeDoisFatores()) {
            String preAuthToken = doisFatoresService.gerarTokenPreAutenticacao(usuario);
            auditoriaService.registrar(usuario, "LOGIN_CONFIGURACAO_2FA_REQUERIDA",
                    "Perfil exige 2FA e a conta ainda não o configurou", ip, true);
            return LoginResponse.requerConfiguracao2FA(preAuthToken);
        }

        return concluirLogin(usuario, ip);
    }


    @Transactional(noRollbackFor = AuthenticationException.class)
    public LoginResponse verificarSegundoFator(String preAuthToken, String codigo, String ip) {
        Usuario usuario = doisFatoresService.buscarPorTokenPreAutenticacao(preAuthToken);

        if (!Boolean.TRUE.equals(usuario.getDoisFatoresAtivo())) {
            throw new BadCredentialsException("Credenciais inválidas");
        }

        validarCodigoDoLogin(usuario, usuario.getDoisFatoresSegredo(), codigo, ip);

        doisFatoresService.invalidarPreAutenticacao(usuario);
        return concluirLogin(usuario, ip);
    }


    @Transactional(noRollbackFor = AuthenticationException.class)
    public ConfiguracaoDoisFatoresResponse iniciarConfiguracaoObrigatoria(String preAuthToken) {
        Usuario usuario = buscarUsuarioEmConfiguracaoObrigatoria(preAuthToken);

        // Renova a validade do token para dar tempo de escanear o QR Code
        doisFatoresService.renovarPreAutenticacao(usuario);
        return doisFatoresService.gerarNovoSegredo(usuario);
    }


    @Transactional(noRollbackFor = AuthenticationException.class)
    public LoginResponse confirmarConfiguracaoObrigatoria(String preAuthToken, String codigo, String ip) {
        Usuario usuario = buscarUsuarioEmConfiguracaoObrigatoria(preAuthToken);

        validarCodigoDoLogin(usuario, usuario.getDoisFatoresSegredo(), codigo, ip);

        doisFatoresService.ativar(usuario, ip);
        doisFatoresService.invalidarPreAutenticacao(usuario);
        return concluirLogin(usuario, ip);
    }


    @Transactional(readOnly = true)
    public void registrarLogout(String email, String ip) {
        usuarioRepository.findByEmail(email).ifPresent(usuario ->
                auditoriaService.registrar(usuario, "LOGOUT", "Logout realizado com sucesso", ip, true));
    }


    private Usuario buscarUsuarioEmConfiguracaoObrigatoria(String preAuthToken) {
        Usuario usuario = doisFatoresService.buscarPorTokenPreAutenticacao(preAuthToken);

        // Este fluxo só vale para quem é obrigado a ter 2FA e ainda não o ativou
        if (Boolean.TRUE.equals(usuario.getDoisFatoresAtivo()) || !usuario.getPerfil().exigeDoisFatores()) {
            throw new BadCredentialsException("Credenciais inválidas");
        }
        return usuario;
    }


    private void validarCodigoDoLogin(Usuario usuario, String segredo, String codigo, String ip) {
        bloqueioContaService.verificarBloqueio(usuario, "LOGIN_2FA_FALHA", ip);

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


    private LoginResponse concluirLogin(Usuario usuario, String ip) {
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