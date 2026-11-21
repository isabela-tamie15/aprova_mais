package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import tcc.ges.aprovamais.entity.enums.MotivoEncerramento;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;

// Correção do status de um estágio pelo administrador. O motivo só é usado no ENCERRADO
@Getter
@Setter
public class CorrecaoStatusEstagioRequest {

    @NotNull(message = "Escolha o novo status")
    private StatusEstagio status;

    private MotivoEncerramento motivoEncerramento;

    @NotBlank(message = "Informe a justificativa da correção")
    @Size(max = 500, message = "A justificativa deve ter no máximo 500 caracteres")
    private String justificativa;
}
