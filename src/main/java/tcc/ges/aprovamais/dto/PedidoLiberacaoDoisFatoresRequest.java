package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PedidoLiberacaoDoisFatoresRequest {

    @NotBlank(message = "O token de pré-autenticação é obrigatório")
    private String token;

    @Size(max = 500, message = "O motivo deve ter no máximo 500 caracteres")
    private String motivo;
}
