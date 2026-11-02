package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

// Um convite enviado, para a lista de quem enviou (sem o token, que só vai no email)
@Getter
@Builder
public class ConviteResponse {
    private final Long id;
    private final String email;
    private final String perfilDestino;
    private final String status;
    private final OffsetDateTime criadoEm;
    private final OffsetDateTime expiracaoToken;
}
