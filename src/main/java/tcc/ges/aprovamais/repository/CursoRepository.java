package tcc.ges.aprovamais.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Curso;

import java.util.Optional;

@Repository
public interface CursoRepository extends JpaRepository<Curso, Long> {

    // Busca um curso pelo código do MEC, usado pra vincular dados importados ou validar cadastro
    Optional<Curso> findByCodigoMec(String codigoMec);
}
