package tcc.ges.aprovamais.service;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/*
   Contas da carga horária do estágio, separadas do service para serem testadas sem banco.
   O aluno não lança horas, e a carga sai das semanas completas desde o início vezes a jornada semanal
*/
public final class CalculadoraCargaHoraria {

    private CalculadoraCargaHoraria() {
    }

    // Semanas necessárias são a carga total dividida pela jornada, arredondada para cima, porque uma semana pela metade ainda precisa ser cumprida
    public static LocalDate calcularDataPrevista(LocalDate dataInicio, BigDecimal cargaTotal,
                                                 BigDecimal jornadaSemanal) {
        if (dataInicio == null || !valoresValidos(cargaTotal, jornadaSemanal)) {
            return null;
        }
        long semanas = cargaTotal.divide(jornadaSemanal, 0, RoundingMode.CEILING).longValue();
        return dataInicio.plusWeeks(semanas);
    }


    /*
       Semanas completas desde o início, usadas no cálculo da carga horária, como 3 semanas × 30h = 90h.
       Antes do início ou sem a data, é zero
    */
    public static long calcularSemanasContadas(LocalDate dataInicio, LocalDate hoje) {
        if (dataInicio == null || hoje == null || hoje.isBefore(dataInicio)) {
            return 0;
        }
        return ChronoUnit.WEEKS.between(dataInicio, hoje);
    }

    //semanas completas desde o início vezes a jornada, sem passar da carga total
    public static BigDecimal calcularCargaCumprida(LocalDate dataInicio, BigDecimal cargaTotal,
                                                   BigDecimal jornadaSemanal, LocalDate hoje) {
        if (!valoresValidos(cargaTotal, jornadaSemanal)) {
            return BigDecimal.ZERO;
        }
        long semanasCompletas = calcularSemanasContadas(dataInicio, hoje);
        BigDecimal cumprida = jornadaSemanal.multiply(BigDecimal.valueOf(semanasCompletas));
        return cumprida.min(cargaTotal);
    }

    // Início usado nas contas, que anda para frente os dias em que o estágio ficou suspenso
    public static LocalDate calcularInicioEfetivo(LocalDate dataInicio, Integer diasSuspensos) {
        if (dataInicio == null || diasSuspensos == null || diasSuspensos <= 0) {
            return dataInicio;
        }
        return dataInicio.plusDays(diasSuspensos);
    }

    /*
       Avisos de prazo. A partir de 30 dias antes do encerramento das entregas o aviso fica amarelo, e
       a partir de 10 dias fica vermelho. No próprio dia fica vermelho com "PRAZO VENCE HOJE", e
       depois da data fica roxo com "PRAZO VENCEU"
    */
    public static final int DIAS_AVISO_AMARELO = 30;
    public static final int DIAS_AVISO_VERMELHO = 10;

    public static final String AVISO_AMARELO = "AMARELO";
    public static final String AVISO_VERMELHO = "VERMELHO";
    public static final String AVISO_ULTIMO_DIA = "ULTIMO_DIA";
    public static final String AVISO_ENCERRADO = "ENCERRADO";

    // Horas que ainda faltam, a carga total menos a cumprida, nunca abaixo de zero
    public static BigDecimal calcularCargaRestante(BigDecimal cargaTotal, BigDecimal cargaCumprida) {
        if (cargaTotal == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal cumprida = cargaCumprida != null ? cargaCumprida : BigDecimal.ZERO;
        return cargaTotal.subtract(cumprida).max(BigDecimal.ZERO);
    }

    /*
       Dias corridos entre hoje e o encerramento das entregas. Fica negativo quando a data já passou
       e nulo quando a turma ainda não tem a data
    */
    public static Long calcularDiasRestantes(LocalDate dataEncerramentoEntregas, LocalDate hoje) {
        if (dataEncerramentoEntregas == null || hoje == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(hoje, dataEncerramentoEntregas);
    }

    /*
       Escolhe o aviso de prazo pelos dias que faltam
       - mais de 30 dias, nenhum aviso
       - de 30 a 11 dias, amarelo
       - de 10 a 1 dia, vermelho
       - no próprio dia, vermelho avisando que é o último dia
       - depois da data, roxo avisando que o prazo venceu
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