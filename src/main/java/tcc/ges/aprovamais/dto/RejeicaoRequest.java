package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejeicaoRequest {

    @NotBlank(message = "A justificativa é obrigatória para rejeitar")
    @Size(max = 1000, message = "A justificativa deve ter no máximo 1000 caracteres")
    private String justificativa;
}