package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TurmaResponse {
    private final Long id;
    private final Long cursoId;
    private final String nomeCurso;
    private final String semestre;
    private final String identificador;
    private final String periodo;
    private final String modalidade;
}
