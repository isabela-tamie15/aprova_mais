package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Convite;
import tcc.ges.aprovamais.entity.enums.StatusConvite;

import java.util.Optional;

@Repository
public interface ConviteRepository extends JpaRepository<Convite, Long> {
    Optional<Convite> findByTokenConvite(String tokenConvite);
    Optional<Convite> findByEmailAndStatus(String email, StatusConvite status);
    boolean existsByEmailAndStatus(String email, StatusConvite status);
}