package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.TipoEstagio;

import java.util.List;

@Repository
public interface TipoEstagioRepository extends JpaRepository<TipoEstagio, Long> {

    List<TipoEstagio> findByCursoId(Long cursoId);
}
