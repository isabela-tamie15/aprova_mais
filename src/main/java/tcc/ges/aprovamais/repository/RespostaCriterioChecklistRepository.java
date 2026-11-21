package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tcc.ges.aprovamais.entity.RespostaCriterioChecklist;

import java.util.List;

public interface RespostaCriterioChecklistRepository extends JpaRepository<RespostaCriterioChecklist, Long> {

    // Respostas de uma versão. São gravadas na ordem do checklist, então o id mantém essa ordem
    List<RespostaCriterioChecklist> findByVersaoIdOrderByIdAsc(Long versaoId);

    // Se o critério já foi usado em alguma correção
    boolean existsByCriterioId(Long criterioId);
}
