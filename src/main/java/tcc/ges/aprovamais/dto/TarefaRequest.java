package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TarefaRequest {

    @NotBlank(message = "Informe o nome da tarefa")
    @Size(min = 2, max = 100, message = "O nome da tarefa deve ter entre 2 e 100 caracteres")
    private String nome;

    @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres")
    private String descricao;

    // Posição da tarefa na trilha, na ordem em que o aluno vê (RN33)
    @NotNull(message = "Informe a ordem da tarefa")
    @Min(value = 1, message = "A ordem deve ser no mínimo 1")
    @Max(value = 99, message = "A ordem deve ser no máximo 99")
    private Integer ordem;

    // Link opcional do material de apoio. Vazio é aceito; preenchido, precisa ser um endereço web
    @Size(max = 500, message = "O link deve ter no máximo 500 caracteres")
    @Pattern(regexp = "^$|^https?://\\S+$", message = "O link deve começar com http:// ou https://")
    private String linkApoio;
}
