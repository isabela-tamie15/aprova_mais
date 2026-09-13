package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TrilhaResponse {
    private final String nomeTipoEstagio;
    private final String nomeTrilha;
    private final List<TarefaResponse> tarefas;
}