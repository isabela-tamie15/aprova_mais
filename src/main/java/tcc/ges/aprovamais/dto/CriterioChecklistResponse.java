package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

// Critério ativo de uma tarefa. O coordenador vê na tela de trilhas, e o orientador no checklist da correção
@Getter
@Builder
public class CriterioChecklistResponse {
    private final Long id;
    private final String descricao;
    private final Integer ordem;
}
