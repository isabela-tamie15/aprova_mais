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
}