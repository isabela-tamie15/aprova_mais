package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.TipoEstagio;

import java.util.List;
import java.util.Optional;

@Repository
public interface TipoEstagioRepository extends JpaRepository<TipoEstagio, Long> {

    // Lista os tipos de estágio disponíveis pra um curso específico
    List<TipoEstagio> findByCursoIdOrderByNomeAsc(Long cursoId);

    // Busca o tipo só se ele for do curso informado, usado no cadastro pra barrar tipo de outro curso
    Optional<TipoEstagio> findByIdAndCursoId(Long id, Long cursoId);
}