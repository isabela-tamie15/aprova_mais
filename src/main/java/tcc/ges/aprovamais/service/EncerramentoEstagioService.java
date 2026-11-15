package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.entity.Aluno;
import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.entity.enums.MotivoEncerramento;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.external.EmailService;
import tcc.ges.aprovamais.repository.EstagioRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/*
   Fim do estágio. O orientador conclui ou reprova, e o coordenador encerra por desligamento.
   Concluído e encerrado são status finais e não mudam mais
*/
@Service
@RequiredArgsConstructor
public class EncerramentoEstagioService {

    private static final Logger log = LoggerFactory.getLogger(EncerramentoEstagioService.class);

    private final EstagioRepository estagioRepository;
    private final UsuarioRepository usuarioRepository;
    private final EstagioService estagioService;
    private final CursoService cursoService;
    private final AuditoriaService auditoriaService;
    private final EmailService emailService;

    @Value("${app.url}")
    private String appUrl;

    // Conclui o estágio ativo quando a carga exigida foi cumprida e todas as tarefas da trilha estão aprovadas
    @Transactional
    public EstagioResponse concluir(Long estagioId, String emailOrientador, String ipOrigem) {
        Estagio estagio = buscarEstagioDoOrientador(estagioId, emailOrientador);

        if (estagio.getStatus() != StatusEstagio.ATIVO) {
            throw new RegraNegocioException("Apenas estágios ativos podem ser concluídos");
        }
        EstagioResponse progresso = estagioService.montarProgresso(estagio);
        if (!Boolean.TRUE.equals(progresso.getPodeConcluir())) {
            throw new RegraNegocioException(
                    "O estágio só pode ser concluído com a carga horária cumprida e todas as tarefas aprovadas");
        }

        estagio.setStatus(StatusEstagio.CONCLUIDO);
        estagio.setMotivoEncerramento(null);
        estagio.setDataFinalizacao(LocalDate.now());
        Estagio salvo = estagioRepository.save(estagio);

        auditoriaService.registrar(buscarUsuario(emailOrientador), "ESTAGIO_CONCLUIDO",
                "Estágio id=" + estagioId + " concluído", ipOrigem, true);
        avisarAluno(salvo);

        log.info("[ESTÁGIO] Estágio {} concluído pelo orientador: {}", estagioId, emailOrientador);
        return estagioService.montarProgresso(salvo);
    }

    // Encerra o estágio ativo por reprovação na avaliação final do orientador
    @Transactional
    public EstagioResponse reprovar(Long estagioId, String emailOrientador, String observacao, String ipOrigem) {
        Estagio estagio = buscarEstagioDoOrientador(estagioId, emailOrientador);

        if (estagio.getStatus() != StatusEstagio.ATIVO) {
            throw new RegraNegocioException("Apenas estágios ativos podem ser reprovados");
        }

        Estagio salvo = encerrar(estagio, MotivoEncerramento.REPROVACAO, observacao);
        auditoriaService.registrar(buscarUsuario(emailOrientador), "ESTAGIO_REPROVADO",
                "Estágio id=" + estagioId + " encerrado por reprovação", ipOrigem, true);
        avisarAluno(salvo);

        log.info("[ESTÁGIO] Estágio {} reprovado pelo orientador: {}", estagioId, emailOrientador);
        return estagioService.montarProgresso(salvo);
    }

    // Encerra por desligamento um estágio em aberto de um curso do coordenador
    @Transactional
    public EstagioResponse desligar(Long estagioId, String emailCoordenador, String observacao, String ipOrigem) {
        Estagio estagio = estagioRepository.findByIdAndMatriculaTurmaCursoCoordenadorEmail(estagioId, emailCoordenador)
                .orElseThrow(() -> new ResourceNotFoundException("Estágio não encontrado"));

        if (!EstagioService.STATUS_EM_ABERTO.contains(estagio.getStatus())) {
            throw new RegraNegocioException("Este estágio já foi finalizado e não pode mudar de status");
        }

        Estagio salvo = encerrar(estagio, MotivoEncerramento.DESLIGAMENTO, observacao);
        auditoriaService.registrar(buscarUsuario(emailCoordenador), "ESTAGIO_DESLIGADO",
                "Estágio id=" + estagioId + " encerrado por desligamento", ipOrigem, true);
        avisarAluno(salvo);

        log.info("[ESTÁGIO] Estágio {} encerrado por desligamento pelo coordenador: {}", estagioId, emailCoordenador);
        return estagioService.montarProgresso(salvo);
    }

    // Estágios em aberto de um curso do coordenador, para a tela de desligamento
    @Transactional(readOnly = true)
    public List<EstagioResponse> listarEmAbertoDoCurso(Long cursoId, String emailCoordenador) {
        cursoService.buscarCursoDoCoordenador(cursoId, emailCoordenador);

        List<EstagioResponse> respostas = new ArrayList<>();
        for (Estagio estagio : estagioRepository.findByMatriculaTurmaCursoIdAndStatusInOrderByMatriculaAlunoNomeAscIdAsc(
                cursoId, EstagioService.STATUS_EM_ABERTO)) {
            respostas.add(estagioService.montarProgresso(estagio));
        }
        return respostas;
    }

    private Estagio encerrar(Estagio estagio, MotivoEncerramento motivo, String observacao) {
        estagio.setStatus(StatusEstagio.ENCERRADO);
        estagio.setMotivoEncerramento(motivo);
        estagio.setObservacaoEncerramento(observacao.trim());
        estagio.setDataFinalizacao(LocalDate.now());
        return estagioRepository.save(estagio);
    }

    // Responde 404 para o estágio de outro orientador, como nas outras telas do orientador
    private Estagio buscarEstagioDoOrientador(Long estagioId, String emailOrientador) {
        return estagioRepository.findByIdAndOrientadorEmail(estagioId, emailOrientador)
                .orElseThrow(() -> new ResourceNotFoundException("Estágio não encontrado"));
    }

    private Usuario buscarUsuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    // O e-mail leva só o resultado e o link. A observação fica no sistema, que exige login
    private void avisarAluno(Estagio estagio) {
        Aluno aluno = estagio.getMatricula().getAluno();
        emailService.enviarFinalizacaoEstagio(aluno.getEmail(), aluno.getNome(),
                estagio.getStatus() == StatusEstagio.CONCLUIDO, appUrl + "/aluno/dashboard");
    }
}
