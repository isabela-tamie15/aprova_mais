package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidadorCnpjTest {

    @Test
    @DisplayName("CNPJ numérico válido passa com ou sem pontuação")
    void deveAceitarCnpjNumericoValido() {
        assertTrue(ValidadorCnpj.valido("11.222.333/0001-81"));
        assertTrue(ValidadorCnpj.valido("11222333000181"));
        assertTrue(ValidadorCnpj.valido("33.000.167/0001-01"));
    }

    @Test
    @DisplayName("CNPJ alfanumérico (exemplo oficial da Receita) é aceito, inclusive em minúsculas")
    void deveAceitarCnpjAlfanumerico() {
        assertTrue(ValidadorCnpj.valido("12.ABC.345/01DE-35"));
        assertTrue(ValidadorCnpj.valido("12abc34501de35"));
    }

    @Test
    @DisplayName("Dígito errado, tamanho errado, letras no dígito e sequências repetidas são recusados")
    void deveRecusarCnpjInvalido() {
        assertFalse(ValidadorCnpj.valido("11.222.333/0001-80"));
        assertFalse(ValidadorCnpj.valido("1122233300018"));
        assertFalse(ValidadorCnpj.valido("12.ABC.345/01DE-3A"));
        assertFalse(ValidadorCnpj.valido("00.000.000/0000-00"));
        assertFalse(ValidadorCnpj.valido("11111111111111"));
        assertFalse(ValidadorCnpj.valido(""));
        assertFalse(ValidadorCnpj.valido(null));
    }

    @Test
    @DisplayName("Normaliza tirando a pontuação e formata de volta pra exibir")
    void deveNormalizarEFormatar() {
        assertEquals("11222333000181", ValidadorCnpj.normalizar(" 11.222.333/0001-81 "));
        assertEquals("11.222.333/0001-81", ValidadorCnpj.formatar("11222333000181"));
        assertEquals("12.ABC.345/01DE-35", ValidadorCnpj.formatar("12abc34501de35"));
    }
}
