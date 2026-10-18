package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class ValidacaoEstagioResponse {
    private final boolean aprovado;
    private final String justificativa;
    private final OffsetDateTime dataDecisao;
    private final String nomeOrientador;
}
