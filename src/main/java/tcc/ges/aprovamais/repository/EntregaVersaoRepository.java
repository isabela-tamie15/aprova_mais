package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.EntregaVersao;

import java.util.List;
import java.util.Optional;

@Repository
public interface EntregaVersaoRepository extends JpaRepository<EntregaVersao, Long> {

    // Histórico da entrega, da versão 1 em diante
    List<EntregaVersao> findByEntregaIdOrderByNumeroAsc(Long entregaId);

    // Versão mais recente, que é a que o orientador avalia
    Optional<EntregaVersao> findFirstByEntregaIdOrderByNumeroDesc(Long entregaId);

    long countByEntregaId(Long entregaId);

    // Download pelo aluno, só versões de entregas do estágio dele
    Optional<EntregaVersao> findByIdAndEntregaEstagioMatriculaAlunoEmail(Long id, String emailAluno);

    // Download pelo orientador, só versões de entregas dos alunos dele
    Optional<EntregaVersao> findByIdAndEntregaEstagioOrientadorEmail(Long id, String emailOrientador);
}
