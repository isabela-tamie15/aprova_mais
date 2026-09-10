package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Turma;

@Repository
public interface TurmaRepository extends JpaRepository<Turma, Long> {
}
