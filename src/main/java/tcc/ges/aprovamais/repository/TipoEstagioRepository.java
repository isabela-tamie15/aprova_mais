package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.TipoEstagio;

import java.util.List;
import java.util.Optional;

@Repository
public interface TipoEstagioRepository extends JpaRepository<TipoEstagio, Long> {

    // Lista os tipos de estágio disponíveis para um curso específico
    List<TipoEstagio> findByCursoIdOrderByNomeAsc(Long cursoId);

    // Busca o tipo só se ele for do curso informado, para barrar tipo de outro curso no cadastro
    Optional<TipoEstagio> findByIdAndCursoId(Long id, Long cursoId);

    // Busca o tipo só se o curso dele for do coordenador informado, senão volta vazio e vira 404
    Optional<TipoEstagio> findByIdAndCursoCoordenadorEmail(Long id, String emailCoordenador);

    // Impede dois tipos com o mesmo nome no mesmo curso, como dois "CLT"
    boolean existsByCursoIdAndNomeIgnoreCase(Long cursoId, String nome);

    boolean existsByCursoIdAndNomeIgnoreCaseAndIdNot(Long cursoId, String nome, Long id);
}