package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TarefaResponse {
    private final Long id;
    private final String nome;
    private final String descricao;
    private final Integer ordem;
    private final String linkApoio;
    // Entrega do aluno nesta tarefa PENDENTE quando nada foi enviado. Nulo na tela do coordenador
    private final String statusEntrega;
    private final Long totalVersoes;
}