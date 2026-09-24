package tcc.ges.aprovamais.auth;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.LoginRequest;
import tcc.ges.aprovamais.dto.LoginResponse;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.UsuarioRepository;
import tcc.ges.aprovamais.security.JwtService;
import tcc.ges.aprovamais.service.AuditoriaService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_TENTATIVAS = 5;
    private static final int MINUTOS_BLOQUEIO = 5;

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    @Transactional
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
                    return new ResourceNotFoundException("Usuário não encontrado");
                });

        // Verifica bloqueio
        if (Boolean.TRUE.equals(usuario.getContaBloqueada())) {
            OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
            if (usuario.getMomentoBloqueio() != null &&
                    agora.isBefore(usuario.getMomentoBloqueio().plusMinutes(MINUTOS_BLOQUEIO))) {

                auditoriaService.registrar(
                        usuario,
                        "LOGIN_FALHA",
                        "Conta bloqueada",
                        ip,
                        false
                );
                throw new LockedException("Conta bloqueada. Tente novamente em alguns minutos.");
            }
            usuario.setContaBloqueada(false);
            usuario.setTentativasFalhas(0);
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            requisicao.getEmail(),
                            requisicao.getSenha()
                    )
            );

            usuario.setTentativasFalhas(0);
            usuario.setContaBloqueada(false);
            usuarioRepository.save(usuario);

            if (Boolean.TRUE.equals(usuario.getDoisFatoresAtivo())) {
                String preAuthToken = java.util.UUID.randomUUID().toString();
                usuario.setTokenPreAutenticacao(preAuthToken);
                usuario.setExpiracaoPreAutenticacao(
                        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(2));
                usuarioRepository.save(usuario);

                auditoriaService.registrar(
                        usuario,
                        "LOGIN_2FA_REQUERIDO",
                        "Aguardando verificação do segundo fator",
                        ip,
                        true
                );

                return LoginResponse.requer2FA(preAuthToken);
            }

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

        } catch (BadCredentialsException e) {
            int tentativas = usuario.getTentativasFalhas() + 1;
            usuario.setTentativasFalhas(tentativas);

            if (tentativas >= MAX_TENTATIVAS) {
                usuario.setContaBloqueada(true);
                usuario.setMomentoBloqueio(OffsetDateTime.now(ZoneOffset.UTC));
                log.warn("[AUTH] Conta bloqueada por excesso de tentativas: {}",
                        usuario.getEmail());
            }

            usuarioRepository.save(usuario);

            auditoriaService.registrar(
                    usuario,
                    "LOGIN_FALHA",
                    "Senha incorreta. Tentativa " + tentativas + " de " + MAX_TENTATIVAS,
                    ip,
                    false
            );

            throw new BadCredentialsException("Credenciais inválidas");
        }
    }
}