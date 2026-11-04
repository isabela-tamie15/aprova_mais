package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

// Entrega de uma tarefa com todo o histórico. O aluno recebe na trilha e o orientador na tela de avaliação
@Getter
@Builder
public class EntregaDetalheResponse {
    // Nulo enquanto o aluno não enviou nada para a tarefa
    private final Long entregaId;
    private final Long tarefaId;
    private final String nomeTarefa;
    private final String descricaoTarefa;
    private final String nomeAluno;

    // PENDENTE (nada enviado), ENVIADA, EM_REVISAO, APROVADA ou DEVOLVIDA
    private final String status;

    // Se o aluno pode mandar um arquivo agora: sem entrega ou com a última versão devolvida (RN56)
    private final boolean podeEnviar;

    private final List<EntregaVersaoResponse> versoes;
}
