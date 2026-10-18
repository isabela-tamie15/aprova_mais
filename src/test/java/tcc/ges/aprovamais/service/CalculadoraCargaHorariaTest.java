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
}
