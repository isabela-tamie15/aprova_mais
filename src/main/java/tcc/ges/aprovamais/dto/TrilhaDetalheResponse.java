package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

// Trilha completa para a tela da coordenação, com o tipo de estágio e as tarefas em ordem
@Getter
@Builder
public class TrilhaDetalheResponse {
    private final Long id;
    private final Long tipoEstagioId;
    private final String nomeTipoEstagio;
    private final String nome;
    private final String descricao;
    private final List<TarefaResponse> tarefas;
}
