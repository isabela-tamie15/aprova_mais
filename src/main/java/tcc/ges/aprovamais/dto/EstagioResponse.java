package tcc.ges.aprovamais.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EstagioResponse {

    private Long id;
    private String status;
    private LocalDate dataInicio;
    private String nomeTipoEstagio;
    private BigDecimal cargaHorariaNecessaria;
    private String nomeOrientador;
    private String nomeAluno;
    private String justificativaRejeicao;
}

