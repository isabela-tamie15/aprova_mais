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


// Esse service é quem cuida do bloqueio temporário de conta depois de várias tentativas de login erradas
@Service
@RequiredArgsConstructor
public class BloqueioContaService {

    private static final Logger log = LoggerFactory.getLogger(BloqueioContaService.class);

    public static final int MAX_TENTATIVAS = 5;
    public static final int MINUTOS_BLOQUEIO = 5;

    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    /*
       Chamado antes de cada tentativa de login ou de validação de 2fa. Se a
       conta não tá bloqueada, sai fora na hora. Se tá bloqueada e o tempo
       ainda não passou, registra na auditoria e recusa. Se já passou, libera
       a conta automaticamente e deixa o fluxo seguir
    */
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

        // Tempo de bloqueio já passou, então libera a conta
        usuario.setContaBloqueada(false);
        usuario.setTentativasFalhas(0);
        usuarioRepository.save(usuario);
    }

    /*
       Soma mais uma falha no contador do usuário. Quando atinge o limite
       definido em MAX_TENTATIVAS, bloqueia a conta e marca o momento exato
       pra depois poder calcular quando liberar. Devolve o número de
       tentativas atualizado pra quem chamou poder usar na mensagem de log
    */
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

    // Zera o contador de falhas e desbloqueia, usado quando o login dá certo
    public void limparFalhas(Usuario usuario) {
        usuario.setTentativasFalhas(0);
        usuario.setContaBloqueada(false);
        usuarioRepository.save(usuario);
    }
}