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
    private final String cnpjEmpresa;
    private final String situacaoCnpj;
    private final String justificativaRejeicao;

    // F2: horas que faltam, data em que as entregas do semestre fecham, dias até essa data e o aviso de prazo
    private final BigDecimal cargaHorariaRestante;
    private final LocalDate dataEncerramentoEntregas;
    private final Long diasParaEncerramentoEntregas;
    private final String avisoPrazo;

    // RF22: semanas completas contadas desde o início, para o extrato da carga. Só vem preenchido no estágio ATIVO
    private final Long semanasContadas;

    /*
       F2: atividades da trilha do tipo de estágio, contadas no service: as aprovadas, o total
       de tarefas e as que ainda faltam. Só vêm preenchidas no estágio ATIVO
    */
    private final Long atividadesAprovadas;
    private final Long atividadesTotal;
    private final Long atividadesRestantes;
}