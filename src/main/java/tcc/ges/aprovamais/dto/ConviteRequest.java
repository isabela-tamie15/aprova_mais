package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tcc.ges.aprovamais.entity.enums.PerfilDestino;

@Data
public class ConviteRequest {

    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "E-mail inválido")
    private String emailDestino;

    @NotNull(message = "O perfil é obrigatório")
    private PerfilDestino perfil;
}