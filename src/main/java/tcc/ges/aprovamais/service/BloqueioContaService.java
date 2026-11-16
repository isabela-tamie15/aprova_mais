package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.entity.enums.ParametroSistema;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;


// Bloqueio temporário da conta depois de várias tentativas erradas de login
@Service
@RequiredArgsConstructor
public class BloqueioContaService {

    private static final Logger log = LoggerFactory.getLogger(BloqueioContaService.class);

    // Tentativas erradas antes do bloqueio, ajustável pelo administrador
    public static int maximoTentativas() {
        return ParametrosVigentes.valor(ParametroSistema.TENTATIVAS_ANTES_BLOQUEIO);
    }

    // Minutos de bloqueio da conta, ajustável pelo administrador
    public static int minutosBloqueio() {
        return ParametrosVigentes.valor(ParametroSistema.MINUTOS_BLOQUEIO);
    }

    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    /*
       Chamado antes de cada tentativa de login ou de código do 2FA. A conta bloqueada dentro do
       prazo é recusada e registrada na auditoria. Passado o prazo, a conta é liberada
    */
    public void verificarBloqueio(Usuario usuario, String acaoAuditoria, String ipOrigem) {
        if (!Boolean.TRUE.equals(usuario.getContaBloqueada())) {
            return;
        }

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        if (usuario.getMomentoBloqueio() != null
                && agora.isBefore(usuario.getMomentoBloqueio().plusMinutes(minutosBloqueio()))) {

            auditoriaService.registrar(usuario, acaoAuditoria, "Conta bloqueada", ipOrigem, false);
            throw new LockedException("Conta bloqueada. Tente novamente em alguns minutos.");
        }

        // Tempo de bloqueio já passou, então libera a conta
        usuario.setContaBloqueada(false);
        usuario.setTentativasFalhas(0);
        usuarioRepository.save(usuario);
    }

    /*
       Soma uma falha no contador. Ao chegar no máximo de tentativas, bloqueia a conta e guarda o momento
       do bloqueio. Devolve o número de tentativas para a mensagem da auditoria
    */
    public int registrarFalha(Usuario usuario) {
        int tentativas = usuario.getTentativasFalhas() + 1;
        usuario.setTentativasFalhas(tentativas);

        if (tentativas >= maximoTentativas()) {
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