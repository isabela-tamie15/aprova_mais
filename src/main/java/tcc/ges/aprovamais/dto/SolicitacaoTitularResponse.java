package tcc.ges.aprovamais.dto;

import java.time.LocalDate;

// Resposta do pedido de acesso ou de correção dos dados (RF43): protocolo e prazo de 15 dias (art. 19 da LGPD)
public record SolicitacaoTitularResponse(String protocolo, LocalDate prazoResposta) {
}
