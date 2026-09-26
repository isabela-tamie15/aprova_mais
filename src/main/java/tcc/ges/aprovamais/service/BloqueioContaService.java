package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;


@Service
@RequiredArgsConstructor
public class BloqueioContaService {

    private static final Logger log = LoggerFactory.getLogger(BloqueioContaService.class);

    public static final int MAX_TENTATIVAS = 5;
    public static final int MINUTOS_BLOQUEIO = 5;

    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;


    public void verificarBloqueio(Usuario usuario, String acaoAuditoria, String ipOrigem) {
        if (!Boolean.TRUE.equals(usuario.getContaBloqueada())) {
            return;
        }

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        if (usuario.getMomentoBloqueio() != null
                && agora.isBefore(usuario.getMomentoBloqueio().plusMinutes(MINUTOS_BLOQUEIO))) {

            auditoriaService.registrar(usuario, acaoAuditoria, "Conta bloqueada", ipOrigem, false);
            throw new LockedException("Conta bloqueada. Tente novamente em alguns minutos.");
        }

        usuario.setContaBloqueada(false);
        usuario.setTentativasFalhas(0);
        usuarioRepository.save(usuario);
    }


    public int registrarFalha(Usuario usuario) {
        int tentativas = usuario.getTentativasFalhas() + 1;
        usuario.setTentativasFalhas(tentativas);

        if (tentativas >= MAX_TENTATIVAS) {
            usuario.setContaBloqueada(true);
            usuario.setMomentoBloqueio(OffsetDateTime.now(ZoneOffset.UTC));
            log.warn("[AUTH] Conta bloqueada por excesso de tentativas: {}", usuario.getEmail());
        }

        usuarioRepository.save(usuario);
        return tentativas;
    }


    public void limparFalhas(Usuario usuario) {
        usuario.setTentativasFalhas(0);
        usuario.setContaBloqueada(false);
        usuarioRepository.save(usuario);
    }
}
