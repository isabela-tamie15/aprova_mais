package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Tarefa;

import java.util.List;
import java.util.Optional;

@Repository
public interface TarefaRepository extends JpaRepository<Tarefa, Long> {

    // Lista as tarefas de uma trilha já na ordem certa pra exibir na tela
    List<Tarefa> findByTrilhaIdOrderByOrdem(Long trilhaId);

    // Busca a tarefa só se a trilha dela for de um curso do coordenador informado (RN21), senão vira 404
    Optional<Tarefa> findByIdAndTrilhaTipoEstagioCursoCoordenadorEmail(Long id, String emailCoordenador);

    // Impede duas tarefas na mesma posição da trilha, assim a ordem que o aluno vê não fica ambígua (RN33)
    boolean existsByTrilhaIdAndOrdem(Long trilhaId, Integer ordem);

    boolean existsByTrilhaIdAndOrdemAndIdNot(Long trilhaId, Integer ordem, Long id);
}
