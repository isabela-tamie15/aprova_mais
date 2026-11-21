package tcc.ges.aprovamais.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.EntregaVersao;
import tcc.ges.aprovamais.entity.enums.SituacaoArquivo;
import tcc.ges.aprovamais.entity.enums.StatusEntrega;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EntregaVersaoRepository extends JpaRepository<EntregaVersao, Long> {

    // Histórico da entrega, da versão 1 em diante
    List<EntregaVersao> findByEntregaIdOrderByNumeroAsc(Long entregaId);

    // Versão mais recente, que é a que o orientador avalia
    Optional<EntregaVersao> findFirstByEntregaIdOrderByNumeroDesc(Long entregaId);

    long countByEntregaId(Long entregaId);

    // Indica se outra versão aponta para o mesmo arquivo, como nos dados de demonstração
    boolean existsByChaveArquivoAndIdNot(String chaveArquivo, Long id);

    // Indica se alguma versão da entrega está com o anexo na situação informada
    boolean existsByEntregaIdAndSituacaoArquivo(Long entregaId, SituacaoArquivo situacaoArquivo);

    // Download pelo aluno, só versões de entregas do estágio dele
    Optional<EntregaVersao> findByIdAndEntregaEstagioMatriculaAlunoEmail(Long id, String emailAluno);

    // Download pelo orientador, só versões de entregas dos alunos dele
    Optional<EntregaVersao> findByIdAndEntregaEstagioOrientadorEmail(Long id, String emailOrientador);


    /*
       Versões que o orientador avaliou. A busca é por quem avaliou, e não pelo orientador atual
       do estágio, então uma troca de turma não mistura os históricos
    */
    Page<EntregaVersao> findByAvaliadorEmailAndResultadoIn(String emailAvaliador, Collection<StatusEntrega> resultados, Pageable pageable);

    /*
       Última versão de cada entrega dos alunos ativos do orientador, filtrada e paginada no banco.
       Filtro nulo deixa passar tudo, e o período já vem no fuso de São Paulo
    */
    @Query(value = """
            SELECT v FROM EntregaVersao v
            JOIN FETCH v.entrega e
            JOIN FETCH e.tarefa t
            JOIN FETCH e.estagio es
            JOIN FETCH es.matricula m
            JOIN FETCH m.aluno a
            WHERE es.orientador.email = :emailOrientador
              AND e.status IN :status
              AND a.ativo = true
              AND v.numero = (SELECT MAX(v2.numero) FROM EntregaVersao v2 WHERE v2.entrega = e)
              AND (:alunoId IS NULL OR a.id = :alunoId)
              AND (:tarefaId IS NULL OR t.id = :tarefaId)
              AND (CAST(:inicio AS OffsetDateTime) IS NULL OR v.enviadaEm >= :inicio)
              AND (CAST(:fim AS OffsetDateTime) IS NULL OR v.enviadaEm < :fim)
            ORDER BY v.enviadaEm ASC, e.id ASC
            """,
            countQuery = """
            SELECT COUNT(v) FROM EntregaVersao v
            JOIN v.entrega e
            JOIN e.estagio es
            WHERE es.orientador.email = :emailOrientador
              AND e.status IN :status
              AND es.matricula.aluno.ativo = true
              AND v.numero = (SELECT MAX(v2.numero) FROM EntregaVersao v2 WHERE v2.entrega = e)
              AND (:alunoId IS NULL OR es.matricula.aluno.id = :alunoId)
              AND (:tarefaId IS NULL OR e.tarefa.id = :tarefaId)
              AND (CAST(:inicio AS OffsetDateTime) IS NULL OR v.enviadaEm >= :inicio)
              AND (CAST(:fim AS OffsetDateTime) IS NULL OR v.enviadaEm < :fim)
            """)
    Page<EntregaVersao> buscarPainelDeCorrecao(@Param("emailOrientador") String emailOrientador,
                                               @Param("status") Collection<StatusEntrega> status,
                                               @Param("alunoId") Long alunoId,
                                               @Param("tarefaId") Long tarefaId,
                                               @Param("inicio") OffsetDateTime inicio,
                                               @Param("fim") OffsetDateTime fim,
                                               Pageable pageable);
}
