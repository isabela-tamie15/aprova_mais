package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Orientador;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrientadorRepository extends JpaRepository<Orientador, Long> {

    // Orientadores com a conta ativa, em ordem alfabética, para o coordenador escolher na atribuição da turma (RF13)
    List<Orientador> findByAtivoTrueOrderByNomeAsc();

    // Busca o orientador só se a conta estiver ativa. Conta pendente, desativada ou anonimizada volta vazio
    Optional<Orientador> findByIdAndAtivoTrue(Long id);

    // Confere se a matrícula institucional já pertence a um orientador, usado no pré-cadastro
    boolean existsByMatriculaInstitucional(String matriculaInstitucional);
}
