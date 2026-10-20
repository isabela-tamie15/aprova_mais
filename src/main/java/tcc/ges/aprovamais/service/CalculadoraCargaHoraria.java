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

    private static boolean valoresValidos(BigDecimal cargaTotal, BigDecimal jornadaSemanal) {
        return cargaTotal != null && jornadaSemanal != null && jornadaSemanal.signum() > 0;
    }
}
