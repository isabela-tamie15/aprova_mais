package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AprovacaoRequest {

    private LocalDate dataInicio;

    // Mesmo limite do cadastro do aluno, assim o orientador não grava uma data absurda
    @AssertTrue(message = "A data de início não pode ser mais de 1 ano no futuro")
    public boolean isDataInicioDentroDoLimite() {
        return EstagioCadastroRequest.dataInicioDentroDoLimite(dataInicio);
    }
}
