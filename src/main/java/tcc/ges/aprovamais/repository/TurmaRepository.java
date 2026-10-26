package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Turma;

import java.util.List;
import java.util.Optional;

@Repository
public interface TurmaRepository extends JpaRepository<Turma, Long> {

    // Lista as turmas de um curso, das mais recentes pras mais antigas, pra tela de turmas
    List<Turma> findByCursoIdOrderBySemestreDescIdentificadorAsc(Long cursoId);

    // Busca a turma só se o curso dela for do coordenador informado (RN21), senão volta vazio e vira 404
    Optional<Turma> findByIdAndCursoCoordenadorEmail(Long id, String emailCoordenador);

    // Impede duas turmas com o mesmo identificador no mesmo semestre do curso (ex: duas 8B em 2026-2)
    boolean existsByCursoIdAndSemestreAndIdentificadorIgnoreCase(Long cursoId, String semestre, String identificador);

    boolean existsByCursoIdAndSemestreAndIdentificadorIgnoreCaseAndIdNot(Long cursoId, String semestre,
                                                                         String identificador, Long id);
}
