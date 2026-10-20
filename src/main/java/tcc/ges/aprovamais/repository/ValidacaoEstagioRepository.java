package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.ValidacaoEstagio;

import java.util.List;

@Repository
public interface ValidacaoEstagioRepository extends JpaRepository<ValidacaoEstagio, Long> {

    // Historico de decisões de um estágio, da mais antiga pra mais recente (o serve de id desempate)
    List<ValidacaoEstagio> findByEstagioIdOrderByDataDecisaoAscIdAsc(Long estagioId);
}
