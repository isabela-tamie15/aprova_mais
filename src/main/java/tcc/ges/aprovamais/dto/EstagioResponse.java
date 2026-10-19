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
    private final String motivoEncerramento;
    private final LocalDate dataInicio;
    private final String nomeTipoEstagio;
    private final BigDecimal cargaHorariaNecessaria;
    private final BigDecimal jornadaSemanal;
    private final BigDecimal cargaHorariaCumprida;
    private final LocalDate dataPrevistaConclusao;
    private final String nomeOrientador;
    private final String nomeAluno;
    private final String nomeEmpresa;
    private final String justificativaRejeicao;
}