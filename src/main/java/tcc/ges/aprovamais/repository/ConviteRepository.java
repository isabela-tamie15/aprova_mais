package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Convite;
import tcc.ges.aprovamais.entity.enums.StatusConvite;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConviteRepository extends JpaRepository<Convite, Long> {

    // Busca um convite pelo token, usado pra validar o link de primeiro acesso
    Optional<Convite> findByTokenConvite(String tokenConvite);

    // Busca convite pelo e-mail e status, usado pra checar se já existe convite pendente
    Optional<Convite> findByEmailAndStatus(String email, StatusConvite status);

    // Versão booleana do de cima, mais rápida quando só quer saber se existe ou não
    boolean existsByEmailAndStatus(String email, StatusConvite status);

    // Lista os convites enviados por uma secretaria, ordenados do mais recente pro mais antigo
    List<Convite> findByRemetenteEmailOrderByCriadoEmDesc(String emailRemetente);
}