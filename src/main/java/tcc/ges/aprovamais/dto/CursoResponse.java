package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CursoResponse {
    private final Long id;
    private final String nome;
    private final String codigoMec;
}
