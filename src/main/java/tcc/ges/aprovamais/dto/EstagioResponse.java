package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class EstagioResponse {

    private final Long id;
    private final String status;
    private final LocalDate dataInicio;
    private final String nomeTipoEstagio;
    private final BigDecimal cargaHorariaNecessaria;
    private final String nomeOrientador;
    private final String nomeAluno;
    private final String justificativaRejeicao;
}