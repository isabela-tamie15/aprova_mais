package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tcc.ges.aprovamais.entity.Matricula;
import tcc.ges.aprovamais.entity.enums.StatusMatricula;

import java.util.Optional;

public interface MatriculaRepository extends JpaRepository<Matricula, Long> {
    Optional<Matricula> findByAlunoIdAndTurmaId(Long alunoId, Long turmaId);
    Optional<Matricula> findFirstByAlunoIdAndStatus(Long alunoId, StatusMatricula status);
}