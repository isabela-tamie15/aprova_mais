package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

// Uma tarefa da trilha na visão consolidada do aluno, com a situação da entrega dela
@Getter
@Builder
public class TarefaVisaoAlunoResponse {
    private final Long tarefaId;
    private final Integer ordem;
    private final String nomeTarefa;

    // Nulo enquanto o aluno não enviou nada para a tarefa
    private final Long entregaId;

    // PENDENTE quando não há entrega; senão, o status da entrega (ENVIADA, EM_REVISAO, APROVADA ou DEVOLVIDA)
    private final String status;

    private final int numeroVersoes;

    // Último feedback escrito pelo orientador, da versão mais recente que tem feedback
    private final String ultimoFeedback;
    private final Integer versaoDoUltimoFeedback;
    private final OffsetDateTime ultimoFeedbackEm;
}
