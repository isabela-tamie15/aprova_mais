package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Orientador;

import java.util.Optional;

@Repository
public interface OrientadorRepository extends JpaRepository<Orientador, Long> {

    @Query(value = "SELECT o.* FROM orientadores o " +
            "INNER JOIN orientadores_turmas ot ON o.id = ot.orientador_id " +
            "WHERE ot.turma_id = :turmaId LIMIT 1",
            nativeQuery = true)
    Optional<Orientador> findFirstByTurmaId(@Param("turmaId") Long turmaId);
}