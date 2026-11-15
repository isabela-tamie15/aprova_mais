package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Trilha;

import java.util.Optional;

@Repository
public interface TrilhaRepository extends JpaRepository<Trilha, Long> {

    // Busca a trilha de um tipo de estágio
    Optional<Trilha> findByTipoEstagioId(Long tipoEstagioId);

    // Confere se o tipo já tem trilha, porque cada tipo tem no máximo uma
    boolean existsByTipoEstagioId(Long tipoEstagioId);

    // Busca a trilha só se o curso do tipo dela for do coordenador informado, senão vira 404
    Optional<Trilha> findByIdAndTipoEstagioCursoCoordenadorEmail(Long id, String emailCoordenador);
}