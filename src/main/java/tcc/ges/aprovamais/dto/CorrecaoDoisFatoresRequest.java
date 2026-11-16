package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// Desativação do 2FA de outro usuário pelo administrador, com justificativa
@Getter
@Setter
public class CorrecaoDoisFatoresRequest {

    @NotBlank(message = "Informe o e-mail do usuário")
    @Email(message = "Informe um e-mail válido")
    private String email;

    @NotBlank(message = "Informe a justificativa da correção")
    @Size(max = 500, message = "A justificativa deve ter no máximo 500 caracteres")
    private String justificativa;
}
