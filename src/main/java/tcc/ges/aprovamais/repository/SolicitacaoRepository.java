package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Solicitacao;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.entity.enums.StatusSolicitacao;
import tcc.ges.aprovamais.entity.enums.TipoSolicitacao;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SolicitacaoRepository extends JpaRepository<Solicitacao, Long> {

    // Indica se o estágio já tem um pedido em análise
    boolean existsByEstagioIdAndStatusIn(Long estagioId, Collection<StatusSolicitacao> statuses);

    // Mesma conferência, ignorando um tipo de pedido
    boolean existsByEstagioIdAndStatusInAndTipoNot(Long estagioId, Collection<StatusSolicitacao> statuses,
                                                   TipoSolicitacao tipo);

    // Indica se a versão já teve algum pedido de exclusão de anexo
    boolean existsByVersaoId(Long versaoId);

    // Pedidos da matrícula do aluno, do mais recente para o mais antigo
    List<Solicitacao> findByEstagioMatriculaIdOrderByCriadoEmDescIdDesc(Long matriculaId);

    // Todos os pedidos do aluno, usados na anonimização
    List<Solicitacao> findByEstagioMatriculaAlunoId(Long alunoId);

    // Busca o pedido só se ele for da matrícula do aluno
    Optional<Solicitacao> findByIdAndEstagioMatriculaId(Long id, Long matriculaId);

    // Pedidos dos cursos do coordenador, para a tela da coordenação
    List<Solicitacao> findByEstagioMatriculaTurmaCursoIdOrderByCriadoEmDescIdDesc(Long cursoId);

    // Busca o pedido só se ele for de um curso do coordenador
    Optional<Solicitacao> findByIdAndEstagioMatriculaTurmaCursoCoordenadorEmail(Long id, String email);

    // Pedidos que passam pela secretaria, que são o trancamento e a reabertura
    List<Solicitacao> findByTipoInOrderByCriadoEmDescIdDesc(Collection<TipoSolicitacao> tipos);

    // Último trancamento aprovado do estágio, que traz o prazo de arrependimento
    Optional<Solicitacao> findFirstByEstagioIdAndTipoAndStatusOrderByIdDesc(Long estagioId, TipoSolicitacao tipo,
                                                                            StatusSolicitacao status);

    // Trancamentos aprovados com o prazo vencido e o estágio ainda suspenso
    List<Solicitacao> findByTipoAndStatusAndPrazoArrependimentoBeforeAndEstagioStatus(
            TipoSolicitacao tipo, StatusSolicitacao status, LocalDate data, StatusEstagio statusEstagio);
}
