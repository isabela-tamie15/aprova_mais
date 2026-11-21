package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;


@Data
public class VerificacaoDoisFatoresRequest {

    @NotBlank(message = "O token de pré-autenticação é obrigatório")
    private String token;

    @NotBlank(message = "O código é obrigatório")
    @Pattern(regexp = "\\d{6}", message = "O código deve ter 6 dígitos")
    private String codigo;
}
