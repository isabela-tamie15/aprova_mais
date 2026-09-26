package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Trilha;

import java.util.Optional;

@Repository
public interface TrilhaRepository extends JpaRepository<Trilha, Long> {

    // Busca a trilha de um tipo de estágio, usado quando cada tipo tem só uma trilha
    Optional<Trilha> findByTipoEstagioId(Long tipoEstagioId);
}