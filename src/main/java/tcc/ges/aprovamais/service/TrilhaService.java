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

// Esse service é quem monta a trilha de tarefas do aluno baseado no estágio ativo dele
@Service
@RequiredArgsConstructor
public class TrilhaService {

    private static final Logger log = LoggerFactory.getLogger(TrilhaService.class);

    private final MatriculaRepository matriculaRepository;
    private final EstagioRepository estagioRepository;
    private final TrilhaRepository trilhaRepository;
    private final TarefaRepository tarefaRepository;

    // Esse é o método principal, monta a trilha completa do aluno seguindo a cadeia matrícula → estágio → trilha → tarefas
    @Transactional(readOnly = true)
    public TrilhaResponse buscarTrilhaDoAluno(String emailAluno) {

        /*
           Cada passo depende do anterior, então se qualquer um falhar a
           gente já para ali com 404. A ordem importa, se não tiver matrícula
           ativa não faz sentido procurar estágio, e por aí vai
        */
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

    // Esse é o método usado quando só tem o id do tipo de estágio em mãos, só repassa pro auxiliar
    @Transactional(readOnly = true)
    public Trilha buscarTrilhaPorTipoEstagio(Long tipoEstagioId) {
        return buscarTrilhaPorTipo(tipoEstagioId);
    }

    // Busca a matrícula ativa do aluno, primeiro passo pra chegar na trilha
    private Matricula buscarMatriculaAtiva(String emailAluno) {
        return matriculaRepository
                .findFirstByAlunoEmailAndStatus(emailAluno, StatusMatricula.ATIVA)
                .orElseThrow(() -> {
                    log.warn("[TRILHA] Matrícula ativa não encontrada para aluno: {}", emailAluno);
                    return new ResourceNotFoundException("Matrícula ativa não encontrada.");
                });
    }

    // Busca o estágio ativo da matrícula, segundo passo pra chegar na trilha
    private Estagio buscarEstagioAtivo(Long matriculaId) {
        return estagioRepository
                .findByMatriculaIdAndStatus(matriculaId, StatusEstagio.ATIVO)
                .orElseThrow(() -> {
                    log.warn("[TRILHA] Estágio ativo não encontrado para matrícula: {}", matriculaId);
                    return new ResourceNotFoundException("Estágio ativo não encontrado.");
                });
    }

    // Busca a trilha vinculada ao tipo de estágio, terceiro passo pra chegar nas tarefas
    private Trilha buscarTrilhaPorTipo(Long tipoEstagioId) {
        return trilhaRepository
                .findByTipoEstagioId(tipoEstagioId)
                .orElseThrow(() -> {
                    log.warn("[TRILHA] Trilha não encontrada para tipo de estágio: {}", tipoEstagioId);
                    return new ResourceNotFoundException(
                            "Trilha não encontrada para este tipo de estágio.");
                });
    }

    // Busca as tarefas da trilha já na ordem certa, e converte cada uma pro DTO de resposta
    private List<TarefaResponse> buscarTarefas(Long trilhaId) {
        return tarefaRepository
                .findByTrilhaIdOrderByOrdem(trilhaId)
                .stream()
                .map(this::toTarefaResponse)
                .toList();
    }

    // Converte a entidade Tarefa pro DTO que vai ser devolvido pro frontend
    private TarefaResponse toTarefaResponse(Tarefa tarefa) {
        return TarefaResponse.builder()
                .id(tarefa.getId())
                .nome(tarefa.getNome())
                .descricao(tarefa.getDescricao())
                .ordem(tarefa.getOrdem())
                .build();
    }
}