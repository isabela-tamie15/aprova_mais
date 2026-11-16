package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfiguracaoRequest {

    @NotNull(message = "Informe o novo valor")
    private Integer valor;
}
