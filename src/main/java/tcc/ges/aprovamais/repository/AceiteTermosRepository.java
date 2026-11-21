package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tcc.ges.aprovamais.entity.AceiteTermos;

import java.util.List;

public interface AceiteTermosRepository extends JpaRepository<AceiteTermos, Long> {

    List<AceiteTermos> findByUsuarioIdOrderByAceitoEmDesc(Long usuarioId);

    boolean existsByUsuarioId(Long usuarioId);
}
