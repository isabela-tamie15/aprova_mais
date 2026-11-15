package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// Atribuição do orientador a uma turma pelo coordenador
@Getter
@Setter
public class AtribuicaoOrientadorRequest {

    @NotNull(message = "Selecione o orientador da turma")
    private Long orientadorId;
}
