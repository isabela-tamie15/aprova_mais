package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Critério de avaliação cadastrado pelo coordenador em uma tarefa da trilha
@Data
public class CriterioChecklistRequest {

    @NotBlank(message = "Informe a descrição do critério")
    @Size(min = 2, max = 200, message = "A descrição deve ter entre 2 e 200 caracteres")
    private String descricao;

    // Posição do critério no checklist, como a ordem das tarefas na trilha
    @NotNull(message = "Informe a ordem do critério")
    @Min(value = 1, message = "A ordem deve ser no mínimo 1")
    @Max(value = 99, message = "A ordem deve ser no máximo 99")
    private Integer ordem;
}
