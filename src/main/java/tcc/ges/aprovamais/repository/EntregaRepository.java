package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Entrega;
import tcc.ges.aprovamais.entity.enums.StatusEntrega;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EntregaRepository extends JpaRepository<Entrega, Long> {

    // A entrega de uma tarefa no estágio, que é única (uk_entrega_estagio_tarefa)
    Optional<Entrega> findByEstagioIdAndTarefaId(Long estagioId, Long tarefaId);

    // Todas as entregas do estágio, para a trilha do aluno mostrar o status de cada tarefa
    List<Entrega> findByEstagioId(Long estagioId);

    /*
       Busca a entrega pelo id E pelo e-mail do orientador do estágio. Um id de
       entrega de outro orientador não é encontrado e vira 404 (RN17)
    */
    Optional<Entrega> findByIdAndEstagioOrientadorEmail(Long id, String emailOrientador);

    // Entregas dos alunos do orientador, filtradas por status, para o painel de correção (RF35)
    List<Entrega> findByEstagioOrientadorEmailAndStatusIn(String emailOrientador, Collection<StatusEntrega> status);

    // Uma tarefa com entregas não pode ser removida da trilha, senão o histórico do aluno perde a referência
    boolean existsByTarefaId(Long tarefaId);
}
