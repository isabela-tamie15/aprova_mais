package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

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
    private final LocalDate dataEncerramentoEntregas;

    // RF13: orientador da turma. Ficam nulos enquanto o coordenador não atribui
    private final Long orientadorId;
    private final String nomeOrientador;
}
