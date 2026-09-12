package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;

import java.util.List;
import java.util.Optional;

public interface EstagioRepository extends JpaRepository<Estagio, Long> {
    List<Estagio> findByStatus(StatusEstagio status);
    Optional<Estagio> findByMatriculaId(Long matriculaId);
    Optional<Estagio> findByMatriculaIdAndStatus(Long matriculaId, StatusEstagio status);
}
