package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Coordenador;

import java.util.Optional;

@Repository
public interface CoordenadorRepository extends JpaRepository<Coordenador, Long> {

    // Busca o coordenador pelo e-mail do token, usado pra saber quem está cadastrando
    Optional<Coordenador> findByEmail(String email);
}
