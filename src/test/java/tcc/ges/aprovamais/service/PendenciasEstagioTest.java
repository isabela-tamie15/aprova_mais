package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
