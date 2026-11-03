package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Avaliação da entrega pelo orientador
@Data
public class AvaliacaoEntregaRequest {

    public enum Decisao { APROVAR, DEVOLVER }

    @NotNull(message = "Escolha aprovar ou devolver")
    private Decisao decisao;

    // Obrigatório na devolução, a regra fica no service
    @Size(max = 2000, message = "O feedback deve ter no máximo 2000 caracteres")
    private String feedback;
}
