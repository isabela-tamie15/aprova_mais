package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// Pedido do aluno para excluir o anexo de uma versão da entrega
@Getter
@Setter
public class ExclusaoAnexoRequest {

    @NotBlank(message = "Informe o motivo da exclusão")
    @Size(max = 1000, message = "O motivo deve ter no máximo 1000 caracteres")
    private String motivo;
}
