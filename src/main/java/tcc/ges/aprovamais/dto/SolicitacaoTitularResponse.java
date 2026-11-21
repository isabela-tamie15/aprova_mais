package tcc.ges.aprovamais.dto;

import java.time.LocalDate;

// Protocolo e prazo de resposta do pedido de acesso ou de correção dos dados, que é de 15 dias pela LGPD
public record SolicitacaoTitularResponse(String protocolo, LocalDate prazoResposta) {
}
