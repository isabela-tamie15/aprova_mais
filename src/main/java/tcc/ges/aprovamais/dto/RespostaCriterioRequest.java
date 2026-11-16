package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tcc.ges.aprovamais.entity.enums.ResultadoCriterio;

// Resposta do orientador a um critério do checklist, enviada junto com a avaliação da entrega
@Data
public class RespostaCriterioRequest {

    @NotNull(message = "Critério inválido")
    private Long criterioId;

    @NotNull(message = "Marque suficiente ou insuficiente em todos os critérios")
    private ResultadoCriterio resultado;

    // Opcional
    @Size(max = 1000, message = "A observação deve ter no máximo 1000 caracteres")
    private String observacao;
}
