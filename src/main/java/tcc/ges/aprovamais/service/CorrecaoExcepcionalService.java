package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.entity.enums.MotivoEncerramento;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.exception.DadoInvalidoException;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.EstagioRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.time.LocalDate;

/*
   Correções que só o administrador faz, em situações fora do fluxo normal. Cada uma exige
   justificativa e fica na auditoria com o valor antigo e o novo
*/
@Service
@RequiredArgsConstructor
public class CorrecaoExcepcionalService {

    private static final Logger log = LoggerFactory.getLogger(CorrecaoExcepcionalService.class);

    private final UsuarioRepository usuarioRepository;
    private final EstagioRepository estagioRepository;
    private final EstagioService estagioService;
    private final AuditoriaService auditoriaService;

    /*
       Desativa o 2FA de um usuário que perdeu o celular. No próximo login ele entra só com a senha e,
       nos perfis que exigem 2FA, configura um novo
    */
    @Transactional
    public void desativarDoisFatores(String emailUsuario, String justificativa, String emailAdministrador, String ipOrigem) {
        Usuario administrador = buscarUsuario(emailAdministrador);
        Usuario usuario = buscarUsuario(emailUsuario.trim().toLowerCase());

        if (usuario.getId().equals(administrador.getId())) {
            throw new RegraNegocioException("Para a sua própria conta, use a tela Segurança");
        }
        if (!Boolean.TRUE.equals(usuario.getDoisFatoresAtivo())) {
            throw new RegraNegocioException("Este usuário não está com o 2FA ativo");
        }

        usuario.setDoisFatoresAtivo(false);
        usuario.setDoisFatoresSegredo(null);
        usuario.setUltimoPassoTotp(null);
        usuario.encerrarSessoes();
        usuarioRepository.save(usuario);

        auditoriaService.registrar(administrador, "CORRECAO_DOIS_FATORES",
                "2FA do usuário id=" + usuario.getId() + " desativado. Justificativa: " + justificativa.trim(), ipOrigem, true);
        log.info("[CORREÇÃO] 2FA do usuário {} desativado pelo administrador", usuario.getId());
    }

    @Transactional(readOnly = true)
    public EstagioResponse buscarEstagio(Long estagioId) {
        return estagioService.montarProgresso(buscarEstagioPorId(estagioId));
    }

    /*
       Corrige o status de um estágio, inclusive o de um estágio concluído ou encerrado. O ENCERRADO
       exige o motivo, e a matrícula continua com no máximo um estágio em aberto
    */
    @Transactional
    public EstagioResponse corrigirStatusEstagio(Long estagioId, StatusEstagio novoStatus, MotivoEncerramento motivo,
                                                 String justificativa, String emailAdministrador, String ipOrigem) {
        Usuario administrador = buscarUsuario(emailAdministrador);
        Estagio estagio = buscarEstagioPorId(estagioId);

        if (novoStatus == StatusEstagio.ENCERRADO && motivo == null) {
            throw new DadoInvalidoException("Escolha o motivo do encerramento");
        }
        MotivoEncerramento novoMotivo = novoStatus == StatusEstagio.ENCERRADO ? motivo : null;
        if (novoStatus == estagio.getStatus() && novoMotivo == estagio.getMotivoEncerramento()) {
            throw new RegraNegocioException("O estágio já está com esse status");
        }

        boolean novoEmAberto = EstagioService.STATUS_EM_ABERTO.contains(novoStatus);
        if (novoEmAberto) {
            estagioRepository.findFirstByMatriculaIdAndStatusInOrderByIdDesc(
                            estagio.getMatricula().getId(), EstagioService.STATUS_EM_ABERTO)
                    .filter(outro -> !outro.getId().equals(estagio.getId()))
                    .ifPresent(outro -> {
                        throw new RegraNegocioException(
                                "A matrícula já tem outro estágio em aberto (id=" + outro.getId() + ")");
                    });
        }

        String antes = descrever(estagio.getStatus(), estagio.getMotivoEncerramento());
        estagio.setStatus(novoStatus);
        estagio.setMotivoEncerramento(novoMotivo);
        if (novoEmAberto) {
            estagio.setDataFinalizacao(null);
        } else if (estagio.getDataFinalizacao() == null) {
            estagio.setDataFinalizacao(LocalDate.now());
        }
        if (novoStatus == StatusEstagio.SUSPENSO && estagio.getSuspensoDesde() == null) {
            estagio.setSuspensoDesde(LocalDate.now());
        }
        if (novoStatus != StatusEstagio.SUSPENSO && novoStatus != StatusEstagio.ENCERRADO) {
            estagio.setSuspensoDesde(null);
        }
        Estagio salvo = estagioRepository.save(estagio);

        auditoriaService.registrar(administrador, "CORRECAO_STATUS_ESTAGIO",
                "Estágio id=" + estagioId + ": de " + antes + " para " + descrever(novoStatus, novoMotivo)
                        + ". Justificativa: " + justificativa.trim(), ipOrigem, true);
        log.info("[CORREÇÃO] Estágio {} corrigido de {} para {}", estagioId, antes, descrever(novoStatus, novoMotivo));
        return estagioService.montarProgresso(salvo);
    }

    private String descrever(StatusEstagio status, MotivoEncerramento motivo) {
        return motivo == null ? status.name() : status.name() + " (" + motivo.name() + ")";
    }

    private Estagio buscarEstagioPorId(Long estagioId) {
        return estagioRepository.findById(estagioId)
                .orElseThrow(() -> new ResourceNotFoundException("Estágio não encontrado"));
    }

    private Usuario buscarUsuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }
}
