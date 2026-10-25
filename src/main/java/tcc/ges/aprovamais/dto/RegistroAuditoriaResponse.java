package tcc.ges.aprovamais.dto;

import tcc.ges.aprovamais.entity.LogAuditoria;

import java.time.OffsetDateTime;

public record RegistroAuditoriaResponse(
        Long id,
        OffsetDateTime criadoEm,
        String email,
        String perfil,
        String acao,
        String detalhes,
        String ipOrigem,
        Boolean sucesso
) {
    public static RegistroAuditoriaResponse de(LogAuditoria registro) {
        return new RegistroAuditoriaResponse(
                registro.getId(),
                registro.getCriadoEm(),
                registro.getEmailTentativa(),
                registro.getPerfil() != null ? registro.getPerfil().name() : null,
                registro.getAcao(),
                registro.getDetalhes(),
                registro.getIpOrigem(),
                registro.getSucesso()
        );
    }
}
