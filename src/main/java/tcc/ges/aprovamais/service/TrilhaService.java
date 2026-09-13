package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.TarefaResponse;
import tcc.ges.aprovamais.dto.TrilhaResponse;
import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.Matricula;
import tcc.ges.aprovamais.entity.Tarefa;
import tcc.ges.aprovamais.entity.Trilha;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.entity.enums.StatusMatricula;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.EstagioRepository;
import tcc.ges.aprovamais.repository.MatriculaRepository;
import tcc.ges.aprovamais.repository.TarefaRepository;
import tcc.ges.aprovamais.repository.TrilhaRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TrilhaService {

    private static final Logger log = LoggerFactory.getLogger(TrilhaService.class);

    private final MatriculaRepository matriculaRepository;
    private final EstagioRepository estagioRepository;
    private final TrilhaRepository trilhaRepository;
    private final TarefaRepository tarefaRepository;

    @Transactional(readOnly = true)
    public TrilhaResponse buscarTrilhaDoAluno(String emailAluno) {

        Matricula matricula = buscarMatriculaAtiva(emailAluno);
        Estagio estagio = buscarEstagioAtivo(matricula.getId());
        Trilha trilha = buscarTrilhaPorTipo(estagio.getTipoEstagio().getId());
        List<TarefaResponse> tarefas = buscarTarefas(trilha.getId());

        log.info("[TRILHA] {} tarefas encontradas para aluno: {} - Tipo: {}",
                tarefas.size(), emailAluno, estagio.getTipoEstagio().getNome());

        return TrilhaResponse.builder()
                .nomeTipoEstagio(estagio.getTipoEstagio().getNome())
                .nomeTrilha(trilha.getNome())
                .tarefas(tarefas)
                .build();
    }

    @Transactional(readOnly = true)
    public Trilha buscarTrilhaPorTipoEstagio(Long tipoEstagioId) {
        return buscarTrilhaPorTipo(tipoEstagioId);
    }

    private Matricula buscarMatriculaAtiva(String emailAluno) {
        return matriculaRepository
                .findFirstByAlunoUsuarioEmailAndStatus(emailAluno, StatusMatricula.ATIVA)
                .orElseThrow(() -> {
                    log.warn("[TRILHA] Matrícula ativa não encontrada para aluno: {}", emailAluno);
                    return new ResourceNotFoundException("Matrícula ativa não encontrada.");
                });
    }

    private Estagio buscarEstagioAtivo(Long matriculaId) {
        return estagioRepository
                .findByMatriculaIdAndStatus(matriculaId, StatusEstagio.ATIVO)
                .orElseThrow(() -> {
                    log.warn("[TRILHA] Estágio ativo não encontrado para matrícula: {}", matriculaId);
                    return new ResourceNotFoundException("Estágio ativo não encontrado.");
                });
    }

    private Trilha buscarTrilhaPorTipo(Long tipoEstagioId) {
        return trilhaRepository
                .findByTipoEstagioId(tipoEstagioId)
                .orElseThrow(() -> {
                    log.warn("[TRILHA] Trilha não encontrada para tipo de estágio: {}", tipoEstagioId);
                    return new ResourceNotFoundException(
                            "Trilha não encontrada para este tipo de estágio.");
                });
    }

    private List<TarefaResponse> buscarTarefas(Long trilhaId) {
        return tarefaRepository
                .findByTrilhaIdOrderByOrdem(trilhaId)
                .stream()
                .map(this::toTarefaResponse)
                .toList();
    }

    private TarefaResponse toTarefaResponse(Tarefa tarefa) {
        return TarefaResponse.builder()
                .id(tarefa.getId())
                .nome(tarefa.getNome())
                .descricao(tarefa.getDescricao())
                .ordem(tarefa.getOrdem())
                .build();
    }
}