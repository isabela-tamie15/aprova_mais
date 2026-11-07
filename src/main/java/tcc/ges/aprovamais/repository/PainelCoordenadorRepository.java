package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import tcc.ges.aprovamais.entity.Entrega;
import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.enums.StatusEntrega;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;

import java.util.Collection;
import java.util.List;

/*
   Consultas do dashboard do coordenador. Este repositório é só de leitura: por isso ele estende
   o Repository, e não o JpaRepository, e assim não herda nenhum método de salvar ou apagar.
   Todas as consultas filtram pelo e-mail do coordenador do curso (RN21), então um coordenador
   nunca recebe dados dos cursos de outro. O filtro de um curso só, quando o coordenador escolhe
   no seletor, é feito depois, no PainelCoordenadorService.
   São 4 consultas principais, não importa quantos estágios existam. Se buscássemos estágio por
   estágio, como a tela do aluno faz, seriam 2 consultas para cada estágio.
   Observação: no JPA, os relacionamentos @ManyToOne são carregados junto por padrão. Por isso
   as consultas trazem também o aluno da matrícula ("join fetch m.aluno"), mesmo sem o painel
   mostrar o aluno: sem isso o Hibernate faria uma consulta extra para cada aluno
*/
public interface PainelCoordenadorRepository extends Repository<Estagio, Long> {

    /*
       Estágios dos cursos do coordenador nos status pedidos (no painel, PENDENTE e ATIVO).
       O "join fetch" já traz a matrícula, o aluno, a turma, o curso, o tipo de estágio e o orientador
       na mesma consulta, para o service não precisar ir ao banco de novo para cada estágio.
       O orientador usa "left join" porque pode estar vazio (é o cartão 1)
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
       Para cada tarefa das trilhas dos cursos do coordenador, devolve o id do tipo de estágio dela.
       Exemplo: se a trilha do tipo 3 tem 3 tarefas, o 3 aparece três vezes na lista.
       O service conta as repetições e chega no total de tarefas de cada tipo
    */
    @Query("""
            select ta.trilha.tipoEstagio.id from Tarefa ta
            where ta.trilha.tipoEstagio.curso.coordenador.email = :email
            """)
    List<Long> listarTipoEstagioDasTarefas(@Param("email") String email);

    /*
       Para cada entrega com o status pedido (no painel, APROVADA), devolve o id do estágio dela,
       só dos estágios no status pedido (no painel, ATIVO). Exemplo: se o estágio 10 tem
       2 entregas aprovadas, o 10 aparece duas vezes. É a mesma contagem da F2 no EstagioService
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
       Entregas dos cursos do coordenador nos status pedidos (no painel, ENVIADA e EM_REVISAO,
       as que aguardam correção). Também traz a turma, o curso e o orientador na mesma consulta
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
}
