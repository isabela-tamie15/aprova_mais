package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class ItemPainelCorrecaoResponse {
    private final Long entregaId;
    private final Long alunoId;
    private final String nomeAluno;
    private final Long tarefaId;
    private final String nomeTarefa;
    private final String status;
    private final Integer numeroVersao;
    private final OffsetDateTime enviadaEm;
}
