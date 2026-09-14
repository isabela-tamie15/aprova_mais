package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.OrientadorTurma;

import java.util.Optional;

@Repository
public interface OrientadorTurmaRepository extends JpaRepository<OrientadorTurma, Long> {
    Optional<OrientadorTurma> findFirstByTurmaId(Long turmaId);
}