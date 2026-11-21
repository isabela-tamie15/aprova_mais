package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.TarefaResponse;
import tcc.ges.aprovamais.dto.TrilhaResponse;
import tcc.ges.aprovamais.entity.Entrega;
import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.Matricula;
import tcc.ges.aprovamais.entity.Tarefa;
import tcc.ges.aprovamais.entity.Trilha;
import tcc.ges.aprovamais.entity.enums.StatusEntrega;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.entity.enums.StatusMatricula;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.EstagioRepository;
import tcc.ges.aprovamais.repository.MatriculaRepository;
import tcc.ges.aprovamais.repository.TarefaRepository;
import tcc.ges.aprovamais.repository.TrilhaRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Trilha de tarefas do aluno, a partir do estágio ativo dele
@Service
@RequiredArgsConstructor
public class TrilhaService {

    private static final Logger log = LoggerFactory.getLogger(TrilhaService.class);

    private final MatriculaRepository matriculaRepository;
    private final EstagioRepository estagioRepository;
    private final TrilhaRepository trilhaRepository;
    private final TarefaRepository tarefaRepository;
    private final EntregaService entregaService;

    // Monta a trilha do aluno seguindo matrícula, estágio, trilha e tarefas
    @Transactional(readOnly = true)
    public TrilhaResponse buscarTrilhaDoAluno(String emailAluno) {

        // Cada passo depende do anterior, e qualquer falha responde 404
        Matricula matricula = buscarMatriculaAtiva(emailAluno);
        Estagio estagio = buscarEstagioAtivo(matricula.getId());
        Trilha trilha = buscarTrilhaPorTipo(estagio.getTipoEstagio().getId());
        List<TarefaResponse> tarefas = buscarTarefas(trilha.getId(), entregaService.entregasPorTarefa(estagio.getId()));

        log.info("[TRILHA] {} tarefas encontradas para aluno: {} - Tipo: {}",
                tarefas.size(), emailAluno, estagio.getTipoEstagio().getNome());

        return TrilhaResponse.builder()
                .nomeTipoEstagio(estagio.getTipoEstagio().getNome())
                .nomeTrilha(trilha.getNome())
                .tarefas(tarefas)
                .build();
    }

    // Monta a trilha a partir do id do tipo de estágio
    @Transactional(readOnly = true)
    public Trilha buscarTrilhaPorTipoEstagio(Long tipoEstagioId) {
        return buscarTrilhaPorTipo(tipoEstagioId);
    }

    // Busca a matrícula ativa do aluno, primeiro passo para chegar na trilha
    private Matricula buscarMatriculaAtiva(String emailAluno) {
        return matriculaRepository
                .findFirstByAlunoEmailAndStatus(emailAluno, StatusMatricula.ATIVA)
                .orElseThrow(() -> {
                    log.warn("[TRILHA] Matrícula ativa não encontrada para aluno: {}", emailAluno);
                    return new ResourceNotFoundException("Matrícula ativa não encontrada.");
                });
    }

    // Busca o estágio ativo da matrícula, segundo passo para chegar na trilha
    private Estagio buscarEstagioAtivo(Long matriculaId) {
        return estagioRepository
                .findByMatriculaIdAndStatus(matriculaId, StatusEstagio.ATIVO)
                .orElseThrow(() -> {
                    log.warn("[TRILHA] Estágio ativo não encontrado para matrícula: {}", matriculaId);
                    return new ResourceNotFoundException("Estágio ativo não encontrado.");
                });
    }

    // Busca a trilha do tipo de estágio
    private Trilha buscarTrilhaPorTipo(Long tipoEstagioId) {
        return trilhaRepository
                .findByTipoEstagioId(tipoEstagioId)
                .orElseThrow(() -> {
                    log.warn("[TRILHA] Trilha não encontrada para tipo de estágio: {}", tipoEstagioId);
                    return new ResourceNotFoundException(
                            "Trilha não encontrada para este tipo de estágio.");
                });
    }

    // Busca as tarefas da trilha já na ordem certa, e converte cada uma para o DTO com o status da entrega do aluno
    private List<TarefaResponse> buscarTarefas(Long trilhaId, Map<Long, Entrega> entregas) {
        List<Tarefa> tarefasDaTrilha = tarefaRepository.findByTrilhaIdOrderByOrdem(trilhaId);

        // Para cada tarefa, montamos a resposta com o status da entrega do aluno naquela tarefa
        List<TarefaResponse> respostas = new ArrayList<>();
        for (Tarefa tarefa : tarefasDaTrilha) {
            respostas.add(toTarefaResponse(tarefa, entregas.get(tarefa.getId())));
        }
        return respostas;
    }

    // Converte a entidade Tarefa para o DTO que vai ser devolvido para o frontend
    private TarefaResponse toTarefaResponse(Tarefa tarefa, Entrega entrega) {
        return TarefaResponse.builder()
                .id(tarefa.getId())
                .nome(tarefa.getNome())
                .descricao(tarefa.getDescricao())
                .ordem(tarefa.getOrdem())
                .linkApoio(tarefa.getLinkApoio())
                //status da entrega da tarefa fica como PENDENTE enquanto o aluno não enviou nada
                .statusEntrega(entrega == null ? StatusEntrega.PENDENTE.name() : entrega.getStatus().name())
                .totalVersoes(entrega == null ? 0L : entregaService.contarVersoes(entrega.getId()))
                .build();
    }
}