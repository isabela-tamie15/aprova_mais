package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.OrientadorTurma;

import java.util.Optional;

@Repository
public interface OrientadorTurmaRepository extends JpaRepository<OrientadorTurma, Long> {

    // Pega a primeira relação de uma turma, usado quando a regra é que a turma tenha só um orientador
    Optional<OrientadorTurma> findFirstByTurmaId(Long turmaId);
}