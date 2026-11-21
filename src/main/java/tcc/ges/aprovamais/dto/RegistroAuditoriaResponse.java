package tcc.ges.aprovamais.dto;

import tcc.ges.aprovamais.config.MascaraEmailConverter;
import tcc.ges.aprovamais.entity.LogAuditoria;

import java.time.OffsetDateTime;

/*
   Linha da tela de auditoria. Na lista, o e-mail e o IP vêm mascarados (mascarado); o registro completo
   só é devolvido pela consulta de um registro (de), que também fica auditada
*/
public record RegistroAuditoriaResponse(
        Long id,
        OffsetDateTime criadoEm,
        String email,
        String perfil,
        String acao,
        String detalhes,
        String ipOrigem,
        Boolean sucesso,
        boolean mascarado
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
                registro.getSucesso(),
                false
        );
    }

    public static RegistroAuditoriaResponse mascarado(LogAuditoria registro) {
        return new RegistroAuditoriaResponse(
                registro.getId(),
                registro.getCriadoEm(),
                MascaraEmailConverter.mascarar(registro.getEmailTentativa()),
                registro.getPerfil() != null ? registro.getPerfil().name() : null,
                registro.getAcao(),
                MascaraEmailConverter.mascarar(registro.getDetalhes()),
                mascararIp(registro.getIpOrigem()),
                registro.getSucesso(),
                true
        );
    }

    // Mantém só o começo do endereço: 172.18.0.1 vira 172.18.*.*
    private static String mascararIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return ip;
        }
        if (ip.contains(":")) {
            String[] partes = ip.split(":");
            return partes[0] + ":" + (partes.length > 1 ? partes[1] : "") + ":*";
        }
        String[] partes = ip.split("\\.");
        return partes.length == 4 ? partes[0] + "." + partes[1] + ".*.*" : "*";
    }
}
