package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejeicaoRequest {

    @NotBlank(message = "A justificativa é obrigatória para rejeitar")
    private String justificativa;
}