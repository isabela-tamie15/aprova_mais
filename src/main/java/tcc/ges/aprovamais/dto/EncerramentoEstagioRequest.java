package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// Observação de quem encerra o estágio por reprovação ou desligamento
@Getter
@Setter
public class EncerramentoEstagioRequest {

    @NotBlank(message = "Informe a observação do encerramento")
    @Size(max = 1000, message = "A observação deve ter no máximo 1000 caracteres")
    private String observacao;
}
