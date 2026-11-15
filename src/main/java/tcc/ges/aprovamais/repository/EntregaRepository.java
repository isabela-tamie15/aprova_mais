package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.dto.OpcaoFiltroResponse;
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

    // Quantas entregas dos alunos do orientador estão em certos status, para o total "aguardando análise" do painel
    long countByEstagioOrientadorEmailAndStatusIn(String emailOrientador, Collection<StatusEntrega> status);

    // Alunos que têm entrega com o orientador nesses status, cada um uma vez, para o filtro "Aluno" do painel
    @Query("""
            SELECT DISTINCT new tcc.ges.aprovamais.dto.OpcaoFiltroResponse(a.id, a.nome)
            FROM Entrega e JOIN e.estagio es JOIN es.matricula m JOIN m.aluno a
            WHERE es.orientador.email = :emailOrientador AND e.status IN :status
            """)
    List<OpcaoFiltroResponse> listarAlunosDoPainel(@Param("emailOrientador") String emailOrientador,
                                                    @Param("status") Collection<StatusEntrega> status);

    // Tarefas que têm entrega com o orientador nesses status, cada uma uma vez, para o filtro "Tarefa" do painel
    @Query("""
            SELECT DISTINCT new tcc.ges.aprovamais.dto.OpcaoFiltroResponse(t.id, t.nome)
            FROM Entrega e JOIN e.estagio es JOIN e.tarefa t
            WHERE es.orientador.email = :emailOrientador AND e.status IN :status
            """)
    List<OpcaoFiltroResponse> listarTarefasDoPainel(@Param("emailOrientador") String emailOrientador,
                                                     @Param("status") Collection<StatusEntrega> status);
}
