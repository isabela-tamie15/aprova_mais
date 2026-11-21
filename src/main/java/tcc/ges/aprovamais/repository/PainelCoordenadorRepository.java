package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import tcc.ges.aprovamais.entity.Entrega;
import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.Turma;
import tcc.ges.aprovamais.entity.enums.StatusEntrega;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;

import java.util.Collection;
import java.util.List;

public interface PainelCoordenadorRepository extends Repository<Estagio, Long> {

    /*
       Estágios dos cursos do coordenador nos status pedidos, que no painel são PENDENTE e ATIVO.
       O join fetch traz matrícula, aluno, turma, curso, tipo de estágio e orientador na mesma
       consulta. O orientador usa left join porque pode estar vazio
    */
    @Query("""
            select e from Estagio e
            join fetch e.matricula m
            join fetch m.aluno a
            join fetch m.turma t
            join fetch t.curso c
            join fetch e.tipoEstagio te
            left join fetch e.orientador o
            where c.coordenador.email = :email
            and e.status in :status
            """)
    List<Estagio> listarEstagiosDosCursos(@Param("email") String email,
                                          @Param("status") Collection<StatusEstagio> status);

    /*
       Id do tipo de estágio de cada tarefa das trilhas dos cursos do coordenador. Uma trilha com 3
       tarefas faz o id do tipo aparecer três vezes, e o service conta as repetições
    */
    @Query("""
            select ta.trilha.tipoEstagio.id from Tarefa ta
            where ta.trilha.tipoEstagio.curso.coordenador.email = :email
            """)
    List<Long> listarTipoEstagioDasTarefas(@Param("email") String email);

    /*
       Id do estágio de cada entrega com o status pedido, só dos estágios no status pedido.
       Um estágio com 2 entregas aprovadas aparece duas vezes, e o service conta as repetições
    */
    @Query("""
            select en.estagio.id from Entrega en
            where en.estagio.matricula.turma.curso.coordenador.email = :email
            and en.estagio.status = :statusEstagio
            and en.status = :statusEntrega
            """)
    List<Long> listarEstagioDasEntregas(@Param("email") String email,
                                        @Param("statusEstagio") StatusEstagio statusEstagio,
                                        @Param("statusEntrega") StatusEntrega statusEntrega);

    /*
       Entregas dos cursos do coordenador nos status pedidos, que no painel são as que esperam
       correção. Traz também a turma, o curso e o orientador na mesma consulta
    */
    @Query("""
            select en from Entrega en
            join fetch en.estagio e
            join fetch e.matricula m
            join fetch m.aluno a
            join fetch m.turma t
            join fetch t.curso c
            left join fetch e.orientador o
            where c.coordenador.email = :email
            and en.status in :status
            """)
    List<Entrega> listarEntregasDosCursos(@Param("email") String email,
                                          @Param("status") Collection<StatusEntrega> status);

    // Turmas dos cursos do coordenador, com o curso, para as opções dos filtros do painel
    @Query("""
            select t from Turma t
            join fetch t.curso c
            where c.coordenador.email = :email
            order by t.semestre desc, t.identificador asc
            """)
    List<Turma> listarTurmasDosCursos(@Param("email") String email);
}
