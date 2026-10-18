package tcc.ges.aprovamais.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AprovacaoRequest {

    /*
       essa é data de início conferida pelo orientador no termo de compromisso da RF21
       ela é opcional, se não vier, vale a data que o aluno informou no cadastro
    */
    private LocalDate dataInicio;
}
