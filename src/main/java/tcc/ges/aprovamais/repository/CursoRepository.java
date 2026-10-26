package tcc.ges.aprovamais.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Curso;

import java.util.List;
import java.util.Optional;

@Repository
public interface CursoRepository extends JpaRepository<Curso, Long> {

    // Busca um curso pelo código do MEC, usado pra vincular dados importados ou validar cadastro
    Optional<Curso> findByCodigoMec(String codigoMec);

    // Lista os cursos do coordenador logado, em ordem alfabética, pra tela de cursos e os selects das outras telas
    List<Curso> findByCoordenadorEmailOrderByNomeAsc(String emailCoordenador);

    /*
       Busca o curso só se ele for do coordenador informado. É o que garante a RN21:
       curso de outro coordenador volta vazio, e o service responde 404, igual a um id
       que não existe
    */
    Optional<Curso> findByIdAndCoordenadorEmail(Long id, String emailCoordenador);

    // Confere se o código e-MEC já está em uso (RN19), no cadastro e na edição
    boolean existsByCodigoMec(String codigoMec);

    boolean existsByCodigoMecAndIdNot(String codigoMec, Long id);
}
