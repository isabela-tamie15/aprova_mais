package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SolicitacaoRecuperacaoRequest {

    @NotBlank(message = "Informe o e-mail")
    @Email(message = "E-mail inválido")
    @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres")
    private String email;
}
