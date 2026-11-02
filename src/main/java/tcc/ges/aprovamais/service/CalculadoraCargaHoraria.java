package tcc.ges.aprovamais.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/*
   Contas da carga horária do estágio, separadas do service pra dar pra testar sem banco. O aluno não lança horas, a carga sai do período,
   pelas semanas completas desde o início vezes a jornada semanal
*/
public final class CalculadoraCargaHoraria {

    private CalculadoraCargaHoraria() {
    }

    /*
       RN54 = semanas necessárias = carga total / jornada, arredondando pra
       cima, porque uma semana pela metade ainda precisa ser cumprida
    */
    public static LocalDate calcularDataPrevista(LocalDate dataInicio, BigDecimal cargaTotal,
                                                 BigDecimal jornadaSemanal) {
        if (dataInicio == null || !valoresValidos(cargaTotal, jornadaSemanal)) {
            return null;
        }
        long semanas = cargaTotal.divide(jornadaSemanal, 0, RoundingMode.CEILING).longValue();
        return dataInicio.plusWeeks(semanas);
    }


    //semanas completas desde o início vezes a jornada, sem passar da carga total
    public static BigDecimal calcularCargaCumprida(LocalDate dataInicio, BigDecimal cargaTotal,
                                                   BigDecimal jornadaSemanal, LocalDate hoje) {
        if (dataInicio == null || hoje == null || !valoresValidos(cargaTotal, jornadaSemanal)
                || hoje.isBefore(dataInicio)) {
            return BigDecimal.ZERO;
        }
        long semanasCompletas = ChronoUnit.WEEKS.between(dataInicio, hoje);
        BigDecimal cumprida = jornadaSemanal.multiply(BigDecimal.valueOf(semanasCompletas));
        return cumprida.min(cargaTotal);
    }

    /*
       F2: avisos de prazo. A partir de 30 dias antes da data de encerramento das
       entregas do semestre, o aviso aparece em amarelo, e a partir de 10 dias ele
       passa para vermelho. No próprio dia e depois da data, o aviso continua
       vermelho, mas com outro texto, por isso cada situação tem o seu nome
    */
    public static final int DIAS_AVISO_AMARELO = 30;
    public static final int DIAS_AVISO_VERMELHO = 10;

    public static final String AVISO_AMARELO = "AMARELO";
    public static final String AVISO_VERMELHO = "VERMELHO";
    public static final String AVISO_ULTIMO_DIA = "ULTIMO_DIA";
    public static final String AVISO_ENCERRADO = "ENCERRADO";

    // Horas que ainda faltam para o aluno: carga total menos a carga cumprida, nunca menor que zero
    public static BigDecimal calcularCargaRestante(BigDecimal cargaTotal, BigDecimal cargaCumprida) {
        if (cargaTotal == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal cumprida = cargaCumprida != null ? cargaCumprida : BigDecimal.ZERO;
        return cargaTotal.subtract(cumprida).max(BigDecimal.ZERO);
    }

    /*
       Dias corridos entre hoje e a data de encerramento das entregas. Quando a data
       já passou, o resultado fica negativo, e é assim que sabemos que as entregas
       foram encerradas. Se a turma ainda não tem a data, devolvemos nulo
    */
    public static Long calcularDiasRestantes(LocalDate dataEncerramentoEntregas, LocalDate hoje) {
        if (dataEncerramentoEntregas == null || hoje == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(hoje, dataEncerramentoEntregas);
    }

    /*
       Decide qual aviso de prazo a tela deve mostrar, a partir dos dias que faltam:
       - mais de 30 dias: nenhum aviso (devolve nulo);
       - de 30 a 11 dias: amarelo;
       - de 10 a 1 dia: vermelho;
       - no próprio dia: vermelho, avisando que é o último dia;
       - depois da data: vermelho, avisando que as entregas foram encerradas
    */
    public static String definirAvisoPrazo(Long diasRestantes) {
        if (diasRestantes == null || diasRestantes > DIAS_AVISO_AMARELO) {
            return null;
        }
        if (diasRestantes < 0) {
            return AVISO_ENCERRADO;
        }
        if (diasRestantes == 0) {
            return AVISO_ULTIMO_DIA;
        }
        if (diasRestantes <= DIAS_AVISO_VERMELHO) {
            return AVISO_VERMELHO;
        }
        return AVISO_AMARELO;
    }

    private static boolean valoresValidos(BigDecimal cargaTotal, BigDecimal jornadaSemanal) {
        return cargaTotal != null && jornadaSemanal != null && jornadaSemanal.signum() > 0;
    }
}
