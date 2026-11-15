package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import tcc.ges.aprovamais.entity.enums.TipoSolicitacao;

// Pedido do aluno. O novo tipo de estágio só é usado na troca de modalidade
@Getter
@Setter
public class SolicitacaoRequest {

    @NotNull(message = "Escolha o tipo da solicitação")
    private TipoSolicitacao tipo;

    private Long novoTipoEstagioId;

    @NotBlank(message = "Informe o motivo da solicitação")
    @Size(max = 1000, message = "O motivo deve ter no máximo 1000 caracteres")
    private String motivo;
}
