package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tcc.ges.aprovamais.entity.CriterioChecklist;

import java.util.List;
import java.util.Optional;

public interface CriterioChecklistRepository extends JpaRepository<CriterioChecklist, Long> {

    // Critérios ativos da tarefa, na ordem do checklist
    List<CriterioChecklist> findByTarefaIdAndAtivoTrueOrderByOrdem(Long tarefaId);

    // Todos os critérios da tarefa, ativos e desativados. Usado ao remover uma tarefa sem entregas
    List<CriterioChecklist> findByTarefaId(Long tarefaId);

    // Critério ativo de uma tarefa dos cursos do coordenador. O de outro coordenador volta vazio e vira 404
    Optional<CriterioChecklist> findByIdAndAtivoTrueAndTarefaTrilhaTipoEstagioCursoCoordenadorEmail(Long id,
                                                                                                    String emailCoordenador);

    // Posição já ocupada por outro critério ativo da mesma tarefa
    boolean existsByTarefaIdAndOrdemAndAtivoTrue(Long tarefaId, Integer ordem);

    boolean existsByTarefaIdAndOrdemAndAtivoTrueAndIdNot(Long tarefaId, Integer ordem, Long id);
}
