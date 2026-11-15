package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.OrientadorTurma;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrientadorTurmaRepository extends JpaRepository<OrientadorTurma, Long> {

    // O vínculo da turma com o orientador. Pela restrição única do turma_id, existe no máximo um (RF13)
    Optional<OrientadorTurma> findByTurmaId(Long turmaId);

    // Vínculos de todas as turmas de um curso, para a lista de turmas mostrar o orientador de cada uma numa consulta só
    List<OrientadorTurma> findByTurmaCursoId(Long cursoId);
}
