package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CalculadoraCargaHorariaTest {

    private static final BigDecimal CARGA_TOTAL = new BigDecimal("270");
    private static final BigDecimal JORNADA_ESTAGIARIO = new BigDecimal("30");
    private static final BigDecimal JORNADA_CLT = new BigDecimal("44");
    private static final LocalDate INICIO = LocalDate.of(2026, 3, 2);

    @Test
    @DisplayName("Estagiário com 270 h e 30 h semanais termina em 9 semanas")
    void deveCalcularDataPrevistaDoEstagiario() {

        assertEquals(LocalDate.of(2026, 5, 4), CalculadoraCargaHoraria.calcularDataPrevista(INICIO, CARGA_TOTAL, JORNADA_ESTAGIARIO));


    }

    @Test
    @DisplayName("Semana incompleta arredonda pra cima: CLT com 270 h precisa de 7 semanas")
    void deveArredondarSemanasParaCima() {

        assertEquals(INICIO.plusWeeks(7), CalculadoraCargaHoraria.calcularDataPrevista(INICIO, CARGA_TOTAL, JORNADA_CLT));


    }

    @Test
    @DisplayName("Sem jornada não tem como calcular a data prevista")
    void deveDevolverNuloSemJornada() {

        assertNull(CalculadoraCargaHoraria.calcularDataPrevista(INICIO, CARGA_TOTAL, null));

    }

    @Test
    @DisplayName("Conta só semanas completas desde o início")
    void deveContarSemanasCompletas() {

        // 23/03 são 3 semanas completas, 25/03 ainda não fecha a quarta
        assertEquals(new BigDecimal("90"), CalculadoraCargaHoraria.calcularCargaCumprida(INICIO, CARGA_TOTAL, JORNADA_ESTAGIARIO, LocalDate.of(2026, 3, 23)));

        assertEquals(new BigDecimal("90"), CalculadoraCargaHoraria.calcularCargaCumprida(INICIO, CARGA_TOTAL, JORNADA_ESTAGIARIO, LocalDate.of(2026, 3, 25)));

    }

    @Test
    @DisplayName("Carga cumprida não passa da carga total")
    void deveLimitarNaCargaTotal() {

        // CLT na 7th semana daria 308h, mas para em 270h
        assertEquals(CARGA_TOTAL, CalculadoraCargaHoraria.calcularCargaCumprida(INICIO, CARGA_TOTAL, JORNADA_CLT, INICIO.plusWeeks(7)));
    }

    @Test
    @DisplayName("Antes da data de início a carga cumprida é zero")
    void deveSerZeroAntesDoInicio() {

        assertEquals(BigDecimal.ZERO, CalculadoraCargaHoraria.calcularCargaCumprida(INICIO, CARGA_TOTAL, JORNADA_ESTAGIARIO, INICIO.minusDays(1)));

    }

    // F2: horas restantes, dias até o encerramento das entregas e aviso de prazo

    // Data de encerramento das entregas usada nos exemplos do plano da F2
    private static final LocalDate ENCERRAMENTO_ENTREGAS = LocalDate.of(2026, 6, 15);

    @Test
    @DisplayName("Horas restantes são a carga total menos a cumprida, sem ficar negativas")
    void deveCalcularCargaRestante() {

        // 270 h no total e 90 h cumpridas: faltam 180 h
        assertEquals(new BigDecimal("180"), CalculadoraCargaHoraria.calcularCargaRestante(CARGA_TOTAL, new BigDecimal("90")));

        // Carga completa: não falta nada, e o resultado nunca fica negativo
        assertEquals(BigDecimal.ZERO, CalculadoraCargaHoraria.calcularCargaRestante(CARGA_TOTAL, new BigDecimal("300")));

    }

    @Test
    @DisplayName("Dias até o encerramento das entregas contam a partir de hoje e ficam negativos depois da data")
    void deveCalcularDiasAteEncerramentoDasEntregas() {

        // De 23/03 até 15/06 são 84 dias
        assertEquals(84L, CalculadoraCargaHoraria.calcularDiasRestantes(ENCERRAMENTO_ENTREGAS, LocalDate.of(2026, 3, 23)));

        // No dia seguinte ao encerramento, o resultado é -1
        assertEquals(-1L, CalculadoraCargaHoraria.calcularDiasRestantes(ENCERRAMENTO_ENTREGAS, LocalDate.of(2026, 6, 16)));

        // Turma sem data informada pelo coordenador
        assertNull(CalculadoraCargaHoraria.calcularDiasRestantes(null, LocalDate.of(2026, 3, 23)));

    }

    @Test
    @DisplayName("Com mais de 30 dias até o encerramento das entregas não aparece aviso")
    void naoDeveMostrarAvisoComMaisDe30Dias() {

        assertNull(CalculadoraCargaHoraria.definirAvisoPrazo(31L));
        assertNull(CalculadoraCargaHoraria.definirAvisoPrazo(null));

    }

    @Test
    @DisplayName("De 30 a 11 dias antes do encerramento das entregas o aviso é amarelo")
    void deveMostrarAvisoAmareloDe30A11Dias() {

        // 16/05 é 30 dias antes de 15/06, e 04/06 é 11 dias antes
        assertEquals(CalculadoraCargaHoraria.AVISO_AMARELO, CalculadoraCargaHoraria.definirAvisoPrazo(
                CalculadoraCargaHoraria.calcularDiasRestantes(ENCERRAMENTO_ENTREGAS, LocalDate.of(2026, 5, 16))));
        assertEquals(CalculadoraCargaHoraria.AVISO_AMARELO, CalculadoraCargaHoraria.definirAvisoPrazo(
                CalculadoraCargaHoraria.calcularDiasRestantes(ENCERRAMENTO_ENTREGAS, LocalDate.of(2026, 6, 4))));

    }

    @Test
    @DisplayName("De 10 a 1 dia antes do encerramento das entregas o aviso é vermelho")
    void deveMostrarAvisoVermelhoDe10A1Dia() {

        // 05/06 é 10 dias antes de 15/06, e 14/06 é 1 dia antes
        assertEquals(CalculadoraCargaHoraria.AVISO_VERMELHO, CalculadoraCargaHoraria.definirAvisoPrazo(
                CalculadoraCargaHoraria.calcularDiasRestantes(ENCERRAMENTO_ENTREGAS, LocalDate.of(2026, 6, 5))));
        assertEquals(CalculadoraCargaHoraria.AVISO_VERMELHO, CalculadoraCargaHoraria.definirAvisoPrazo(
                CalculadoraCargaHoraria.calcularDiasRestantes(ENCERRAMENTO_ENTREGAS, LocalDate.of(2026, 6, 14))));

    }

    @Test
    @DisplayName("No dia do encerramento avisa que é o último dia, e depois avisa que as entregas foram encerradas")
    void deveAvisarUltimoDiaEEncerramento() {

        assertEquals(CalculadoraCargaHoraria.AVISO_ULTIMO_DIA, CalculadoraCargaHoraria.definirAvisoPrazo(
                CalculadoraCargaHoraria.calcularDiasRestantes(ENCERRAMENTO_ENTREGAS, LocalDate.of(2026, 6, 15))));
        assertEquals(CalculadoraCargaHoraria.AVISO_ENCERRADO, CalculadoraCargaHoraria.definirAvisoPrazo(
                CalculadoraCargaHoraria.calcularDiasRestantes(ENCERRAMENTO_ENTREGAS, LocalDate.of(2026, 6, 16))));

    }

    // RF22: semanas contadas, usadas no extrato da carga

    @Test
    @DisplayName("Três semanas completas desde o início contam 3 semanas, e a semana incompleta não conta")
    void deveContarSomenteSemanasCompletas() {

        // INICIO é 02/03/2026: 23/03 fecha a terceira semana, 22/03 ainda está nela
        assertEquals(3, CalculadoraCargaHoraria.calcularSemanasContadas(INICIO, LocalDate.of(2026, 3, 23)));
        assertEquals(2, CalculadoraCargaHoraria.calcularSemanasContadas(INICIO, LocalDate.of(2026, 3, 22)));

    }

    @Test
    @DisplayName("Antes do início, ou sem a data de início, nenhuma semana é contada")
    void naoDeveContarSemanasAntesDoInicioOuSemData() {

        assertEquals(0, CalculadoraCargaHoraria.calcularSemanasContadas(INICIO, LocalDate.of(2026, 3, 1)));
        assertEquals(0, CalculadoraCargaHoraria.calcularSemanasContadas(null, LocalDate.of(2026, 3, 23)));

    }

    @Test
    @DisplayName("Extrato do CA01: 3 semanas de Estagiário dão 90 h de carga cumprida")
    void deveFecharAContaDoExtrato() {

        LocalDate hoje = LocalDate.of(2026, 3, 23);
        long semanas = CalculadoraCargaHoraria.calcularSemanasContadas(INICIO, hoje);

        assertEquals(0, JORNADA_ESTAGIARIO.multiply(BigDecimal.valueOf(semanas)).compareTo(new BigDecimal("90")));
        assertEquals(0, CalculadoraCargaHoraria.calcularCargaCumprida(INICIO, CARGA_TOTAL, JORNADA_ESTAGIARIO, hoje)
                .compareTo(new BigDecimal("90")));

    }
}