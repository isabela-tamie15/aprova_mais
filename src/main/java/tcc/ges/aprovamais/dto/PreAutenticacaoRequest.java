package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;


@Data
public class PreAutenticacaoRequest {

    @NotBlank(message = "O token de pré-autenticação é obrigatório")
    private String token;
}
