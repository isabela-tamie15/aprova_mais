package tcc.ges.aprovamais.dto;

import java.time.OffsetDateTime;

public record PedidoLiberacaoDoisFatoresResponse(Long id, String nome, String email, String perfil,
                                                 String motivo, OffsetDateTime criadoEm) {
}
