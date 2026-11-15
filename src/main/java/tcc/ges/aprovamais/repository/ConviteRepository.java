package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.entity.Convite;
import tcc.ges.aprovamais.entity.enums.StatusConvite;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConviteRepository extends JpaRepository<Convite, Long> {

    // Busca um convite pelo hash do token (o banco não guarda o token), usado para validar o link de primeiro acesso
    Optional<Convite> findByTokenConvite(String tokenConvite);

    // Busca convite pelo e-mail e status, usado para checar se já existe convite pendente
    Optional<Convite> findByEmailAndStatus(String email, StatusConvite status);

    // Versão booleana do de cima, mais rápida quando só quer saber se existe ou não
    boolean existsByEmailAndStatus(String email, StatusConvite status);

    // Lista os convites enviados por uma secretaria, ordenados do mais recente para o mais antigo
    List<Convite> findByRemetenteEmailOrderByCriadoEmDesc(String emailRemetente);

    //Marca o convite como expirado numa transação própria (REQUIRES_NEW).
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("UPDATE Convite c SET c.status = :status WHERE c.id = :id")
    int atualizarStatus(@Param("id") Long id, @Param("status") StatusConvite status);

    /*
       Marca como EXPIRADO todo convite PENDENTE que passou da validade. Antes o status só
       mudava quando alguém abria o link vencido, e um convite que ninguém abriu continuava
       PENDENTE para sempre, barrando um convite novo para o mesmo e-mail
    */
    @Modifying
    @Query("UPDATE Convite c SET c.status = tcc.ges.aprovamais.entity.enums.StatusConvite.EXPIRADO "
            + "WHERE c.status = tcc.ges.aprovamais.entity.enums.StatusConvite.PENDENTE AND c.expiracaoToken < :agora")
    int expirarVencidos(@Param("agora") OffsetDateTime agora);

    // Mesma coisa, só para os convites de um e-mail, usado logo antes de enviar um convite novo para ele
    @Modifying
    @Query("UPDATE Convite c SET c.status = tcc.ges.aprovamais.entity.enums.StatusConvite.EXPIRADO "
            + "WHERE c.email = :email AND c.status = tcc.ges.aprovamais.entity.enums.StatusConvite.PENDENTE "
            + "AND c.expiracaoToken < :agora")
    int expirarVencidosDoEmail(@Param("email") String email, @Param("agora") OffsetDateTime agora);

    // Troca o e-mail nos convites da pessoa anonimizada
    @Modifying
    @Query("UPDATE Convite c SET c.email = :emailNovo WHERE c.email = :emailAntigo")
    int trocarEmail(@Param("emailAntigo") String emailAntigo, @Param("emailNovo") String emailNovo);
}
