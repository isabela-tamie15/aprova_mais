package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tcc.ges.aprovamais.entity.LogAuditoria;
import tcc.ges.aprovamais.entity.enums.PerfilAuditoria;

import java.time.OffsetDateTime;
import java.util.List;

public interface LogAuditoriaRepository extends JpaRepository<LogAuditoria, Long> {

    List<LogAuditoria> findByUsuarioId(Long usuarioId);

    List<LogAuditoria> findByEmailTentativa(String emailTentativa);

    List<LogAuditoria> findByPerfil(PerfilAuditoria perfil);

    List<LogAuditoria> findByAcao(String acao);

    List<LogAuditoria> findBySucesso(Boolean sucesso);

    List<LogAuditoria> findByCriadoEmBetween(OffsetDateTime inicio, OffsetDateTime fim);
}