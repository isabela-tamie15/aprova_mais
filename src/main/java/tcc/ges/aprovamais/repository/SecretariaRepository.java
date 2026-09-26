package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Secretaria;

@Repository
public interface SecretariaRepository extends JpaRepository<Secretaria, Long> {
}