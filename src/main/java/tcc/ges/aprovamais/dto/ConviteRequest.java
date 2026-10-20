package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tcc.ges.aprovamais.entity.enums.PerfilDestino;

@Data
public class ConviteRequest {

    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "E-mail inválido")
    @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres")
    private String emailDestino;

    @NotNull(message = "O perfil é obrigatório")
    private PerfilDestino perfil;
}