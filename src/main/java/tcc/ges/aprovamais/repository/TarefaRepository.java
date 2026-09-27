package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Tarefa;

import java.util.List;

@Repository
public interface TarefaRepository extends JpaRepository<Tarefa, Long> {

    // Lista as tarefas de uma trilha já na ordem certa pra exibir na tela
    List<Tarefa> findByTrilhaIdOrderByOrdem(Long trilhaId);
}
