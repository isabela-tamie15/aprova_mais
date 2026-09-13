package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EstagioCadastroRequest {

    @NotNull(message = "Informe a data de início do estágio")
    private LocalDate dataInicio;

    @NotNull(message = "Selecione o perfil do estágio")
    private Long tipoEstagioId;
}