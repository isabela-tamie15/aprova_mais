package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendenciasEstagioTest {

    // F2: atividades restantes e pendência, sem banco e sem mock, como no CalculadoraCargaHorariaTest

    @Test
    @DisplayName("Com 3 tarefas e 1 aprovada faltam 2 atividades")
    void deveCalcularAtividadesRestantes() {
        assertEquals(2L, PendenciasEstagio.calcularAtividadesRestantes(3, 1));
        assertEquals(0L, PendenciasEstagio.calcularAtividadesRestantes(3, 3));
        assertEquals(3L, PendenciasEstagio.calcularAtividadesRestantes(3, 0));
    }

    @Test
    @DisplayName("Atividades restantes nunca ficam negativas")
    void naoDeveDeixarAtividadesRestantesNegativas() {
        assertEquals(0L, PendenciasEstagio.calcularAtividadesRestantes(2, 3));
        assertEquals(0L, PendenciasEstagio.calcularAtividadesRestantes(0, 0));
    }

    @Test
    @DisplayName("Tem pendência enquanto alguma tarefa da trilha não foi aprovada")
    void deveTerPendenciaQuandoFaltaAprovar() {
        assertTrue(PendenciasEstagio.temPendencia(3, 1));
        assertTrue(PendenciasEstagio.temPendencia(3, 0));
        assertTrue(PendenciasEstagio.temPendencia(1, 0));
    }

    @Test
    @DisplayName("Sem pendência quando todas as tarefas da trilha foram aprovadas")
    void naoDeveTerPendenciaComTodasAprovadas() {
        assertFalse(PendenciasEstagio.temPendencia(3, 3));
    }

    @Test
    @DisplayName("Trilha sem tarefas não tem pendência, porque não há atividade para entregar")
    void naoDeveTerPendenciaComTrilhaSemTarefas() {
        assertFalse(PendenciasEstagio.temPendencia(0, 0));
    }

    // Dashboard do coordenador: horas que não fecham no prazo (cartão 6)

    @Test
    @DisplayName("Horas não fecham quando a data prevista cai depois do encerramento das entregas")
    void deveTerPendenciaDeHorasQuandoDataPrevistaPassaDoEncerramento() {
        // João termina as horas em 15/12, mas as entregas da turma 8B fecham em 10/12
        assertTrue(PendenciasEstagio.temPendenciaDeHoras(LocalDate.of(2026, 12, 15), LocalDate.of(2026, 12, 10)));
    }

    @Test
    @DisplayName("Horas fecham no prazo quando a data prevista é antes ou no mesmo dia do encerramento")
    void naoDeveTerPendenciaDeHorasQuandoFechamNoPrazo() {
        // Antes do encerramento
        assertFalse(PendenciasEstagio.temPendenciaDeHoras(LocalDate.of(2026, 12, 15), LocalDate.of(2026, 12, 20)));
        // No mesmo dia: o aluno completa as horas no próprio último dia das entregas
        assertFalse(PendenciasEstagio.temPendenciaDeHoras(LocalDate.of(2026, 12, 15), LocalDate.of(2026, 12, 15)));
    }

    @Test
    @DisplayName("Sem uma das datas não há como comparar, então não conta como pendência de horas")
    void naoDeveTerPendenciaDeHorasSemAsDatas() {
        assertFalse(PendenciasEstagio.temPendenciaDeHoras(null, LocalDate.of(2026, 12, 15)));
        assertFalse(PendenciasEstagio.temPendenciaDeHoras(LocalDate.of(2026, 12, 15), null));
    }
}
