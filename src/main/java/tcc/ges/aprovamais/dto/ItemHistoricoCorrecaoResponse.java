package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

// Uma correção do histórico do orientador: uma versão que ele aprovou ou devolveu
@Getter
@Builder
public class ItemHistoricoCorrecaoResponse {
    private final Long entregaId;
    private final String nomeAluno;
    private final String nomeTarefa;
    private final Integer numeroVersao;

    // APROVADA ou DEVOLVIDA, o resultado que o orientador deu a essa versão
    private final String resultado;
    private final String feedback;
    private final OffsetDateTime avaliadaEm;
}
