package tcc.ges.aprovamais.auth;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.LoginRequest;
import tcc.ges.aprovamais.dto.LoginResponse;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.repository.UsuarioRepository;
import tcc.ges.aprovamais.security.JwtService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_TENTATIVAS = 5;
    private static final int MINUTOS_BLOQUEIO = 5;
    private static final String LOG_PREFIX = "[AUTH]";

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;

    @Transactional
    public LoginResponse login(LoginRequest requisicao) {

        Usuario usuario = usuarioRepository.findByEmail(requisicao.getEmail())
                .orElseThrow(() -> {
                    log.warn("{} Tentativa de login com e-mail inexistente: {}",
                            LOG_PREFIX, requisicao.getEmail());
                    return new BadCredentialsException("Credenciais inválidas.");
                });

        if (!usuario.getAtivo()) {
            log.warn("{} Tentativa de login em conta inativa: {}",
                    LOG_PREFIX, usuario.getEmail());
            throw new LockedException("Conta desativada. Entre em contato com o coordenador.");
        }

        if (usuario.getContaBloqueada()) {
            OffsetDateTime desbloqueioEm =
                    usuario.getMomentoBloqueio().plusMinutes(MINUTOS_BLOQUEIO);

            if (OffsetDateTime.now(ZoneOffset.UTC).isBefore(desbloqueioEm)) {
                log.warn("{} Tentativa de login em conta bloqueada: {}",
                        LOG_PREFIX, usuario.getEmail());
                throw new LockedException(
                        "Conta bloqueada. Tente novamente após "
                                + MINUTOS_BLOQUEIO + " minutos."
                );
            }

            desbloquearConta(usuario);
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            requisicao.getEmail(),
                            requisicao.getSenha()
                    )
            );
        } catch (AuthenticationException e) {
            tratarFalhaDeLogin(usuario);
            throw new BadCredentialsException("Credenciais inválidas.");
        }

        desbloquearConta(usuario);

        if (Boolean.TRUE.equals(usuario.getDoisFatoresAtivo())) {
            String preAuthToken = UUID.randomUUID().toString();
            usuario.setTokenPreAutenticacao(preAuthToken);
            usuario.setExpiracaoPreAutenticacao(
                    OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(2));
            usuarioRepository.save(usuario);

            log.info("{} 2FA necessário para: {}", LOG_PREFIX, usuario.getEmail());
            return LoginResponse.requer2FA(preAuthToken);
        }

        usuarioRepository.save(usuario);

        String token = jwtService.gerarToken(
                usuario.getEmail(),
                usuario.getPerfil().name()
        );

        log.info("{} Login bem-sucedido: {} - Perfil: {}",
                LOG_PREFIX, usuario.getEmail(), usuario.getPerfil());

        return LoginResponse.builder()
                .token(token)
                .nome(usuario.getNome())
                .email(usuario.getEmail())
                .perfil(usuario.getPerfil())
                .requer2FA(false)
                .build();
    }

    private void desbloquearConta(Usuario usuario) {
        usuario.setContaBloqueada(false);
        usuario.setTentativasFalhas(0);
        usuario.setMomentoBloqueio(null);
    }

    private void tratarFalhaDeLogin(Usuario usuario) {
        int tentativas = usuario.getTentativasFalhas() + 1;
        usuario.setTentativasFalhas(tentativas);

        if (tentativas >= MAX_TENTATIVAS) {
            usuario.setContaBloqueada(true);
            usuario.setMomentoBloqueio(OffsetDateTime.now(ZoneOffset.UTC));
            log.warn("{} Conta bloqueada por excesso de tentativas: {}",
                    LOG_PREFIX, usuario.getEmail());
        } else {
            log.warn("{} Senha inválida para: {}. Tentativas: {}/{}",
                    LOG_PREFIX, usuario.getEmail(), tentativas, MAX_TENTATIVAS);
        }

        usuarioRepository.save(usuario);
    }
}