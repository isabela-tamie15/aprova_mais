package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.LogAuditoria;
import tcc.ges.aprovamais.entity.enums.PerfilAuditoria;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface LogAuditoriaRepository extends JpaRepository<LogAuditoria, Long> {

    // Lista os logs de um usuário específico, usado na tela de histórico dele
    List<LogAuditoria> findByUsuarioId(Long usuarioId);

    // Busca logs por e-mail tentado, usado pra rastrear tentativas em e-mails que nem existem na base
    List<LogAuditoria> findByEmailTentativa(String emailTentativa);

    // Filtra por perfil do usuário que gerou o log, útil pra relatórios por tipo de conta
    List<LogAuditoria> findByPerfil(PerfilAuditoria perfil);

    // Filtra por tipo de ação, tipo LOGIN_SUCESSO, LOGIN_FALHA, etc
    List<LogAuditoria> findByAcao(String acao);

    // Filtra por sucesso ou falha, usado pra auditorias e relatórios de segurança
    List<LogAuditoria> findBySucesso(Boolean sucesso);

    // Filtra por intervalo de datas, usado em relatórios e consultas de LGPD
    List<LogAuditoria> findByCriadoEmBetween(OffsetDateTime inicio, OffsetDateTime fim);
}