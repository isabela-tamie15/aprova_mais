package tcc.ges.aprovamais.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidadorConfiguracaoTest {

    @Test
    @DisplayName("Chave aleatória com 32 bytes ou mais é aceita")
    void deveAceitarChaveAleatoria() {
        assertDoesNotThrow(() -> ValidadorConfiguracao.validarChave("JWT_SECRET", "q8Z3vN1pT7xL0cR5mW2yB9eK4hJ6sD0aF"));
    }

    @Test
    @DisplayName("Chave vazia, curta, repetitiva ou de exemplo impede a aplicação de subir")
    void deveRecusarChavesFracas() {
        assertThrows(IllegalStateException.class, () -> ValidadorConfiguracao.validarChave("JWT_SECRET", ""));
        assertThrows(IllegalStateException.class, () -> ValidadorConfiguracao.validarChave("JWT_SECRET", "curta123"));
        assertThrows(IllegalStateException.class,
                () -> ValidadorConfiguracao.validarChave("JWT_SECRET", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        assertThrows(IllegalStateException.class,
                () -> ValidadorConfiguracao.validarChave("JWT_SECRET", "aprova_mais_chave_secreta_super_longa_2026"));
        assertThrows(IllegalStateException.class,
                () -> ValidadorConfiguracao.validarChave("ENCRYPTION_SECRET", "troque_por_uma_chave_aleatoria_longa"));
    }
}
