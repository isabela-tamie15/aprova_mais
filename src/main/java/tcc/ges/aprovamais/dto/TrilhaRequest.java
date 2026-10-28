package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TrilhaRequest {

    @NotNull(message = "Selecione o tipo de estágio")
    @Positive(message = "Tipo de estágio inválido")
    private Long tipoEstagioId;

    @NotBlank(message = "Informe o nome da trilha")
    @Size(min = 2, max = 100, message = "O nome da trilha deve ter entre 2 e 100 caracteres")
    private String nome;

    @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres")
    private String descricao;
}
