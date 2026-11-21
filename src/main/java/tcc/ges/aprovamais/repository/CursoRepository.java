package tcc.ges.aprovamais.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Curso;

import java.util.List;
import java.util.Optional;

@Repository
public interface CursoRepository extends JpaRepository<Curso, Long> {

    // Busca um curso pelo código do MEC, usado para vincular dados importados ou validar cadastro
    Optional<Curso> findByCodigoMec(String codigoMec);

    // Lista os cursos do coordenador logado, em ordem alfabética, para a tela de cursos e os selects das outras telas
    List<Curso> findByCoordenadorEmailOrderByNomeAsc(String emailCoordenador);

    Optional<Curso> findByIdAndCoordenadorEmail(Long id, String emailCoordenador);

    // Confere se o código e-MEC já está em uso, no cadastro e na edição
    boolean existsByCodigoMec(String codigoMec);

    boolean existsByCodigoMecAndIdNot(String codigoMec, Long id);

    // Coordenador com curso não pode se anonimizar
    boolean existsByCoordenadorId(Long coordenadorId);
}
