package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// Corpo da atribuição do orientador a uma turma pelo coordenador (RF13)
@Getter
@Setter
public class AtribuicaoOrientadorRequest {

    @NotNull(message = "Selecione o orientador da turma")
    private Long orientadorId;
}
