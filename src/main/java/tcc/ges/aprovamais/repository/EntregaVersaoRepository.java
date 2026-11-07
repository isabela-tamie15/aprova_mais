package tcc.ges.aprovamais.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.EntregaVersao;
import tcc.ges.aprovamais.entity.enums.StatusEntrega;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EntregaVersaoRepository extends JpaRepository<EntregaVersao, Long> {

    // Histórico da entrega, da versão 1 em diante
    List<EntregaVersao> findByEntregaIdOrderByNumeroAsc(Long entregaId);

    // Versão mais recente, que é a que o orientador avalia
    Optional<EntregaVersao> findFirstByEntregaIdOrderByNumeroDesc(Long entregaId);

    long countByEntregaId(Long entregaId);

    // Download pelo aluno, só versões de entregas do estágio dele
    Optional<EntregaVersao> findByIdAndEntregaEstagioMatriculaAlunoEmail(Long id, String emailAluno);

    // Download pelo orientador, só versões de entregas dos alunos dele
    Optional<EntregaVersao> findByIdAndEntregaEstagioOrientadorEmail(Long id, String emailOrientador);


    //Correções feitas pelo orientador: as versões que ele mesmo avaliou. A busca é por quem avaliou, e não pelo orientador atual do estágio, então uma troca de turma não mistura os históricos
    Page<EntregaVersao> findByAvaliadorEmailAndResultadoIn(String emailAvaliador, Collection<StatusEntrega> resultados, Pageable pageable);
}
