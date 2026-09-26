package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Matricula;
import tcc.ges.aprovamais.entity.enums.StatusMatricula;

import java.util.Optional;

@Repository
public interface MatriculaRepository extends JpaRepository<Matricula, Long> {

    // Busca a matrícula de um aluno numa turma específica, pra evitar duplicidade
    Optional<Matricula> findByAlunoIdAndTurmaId(Long alunoId, Long turmaId);

    // Pega a primeira matrícula ativa do aluno, usado quando só pode ter uma por vez
    Optional<Matricula> findFirstByAlunoIdAndStatus(Long alunoId, StatusMatricula status);

    // Versão por e-mail, usada quando o dado disponível é o e-mail do token em vez do id
    Optional<Matricula> findFirstByAlunoEmailAndStatus(String email, StatusMatricula status);
}
