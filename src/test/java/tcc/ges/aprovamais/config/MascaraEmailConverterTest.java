package tcc.ges.aprovamais.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MascaraEmailConverterTest {

    @Test
    @DisplayName("E-mail no log fica só com a inicial e o domínio")
    void deveMascararEmails() {
        assertEquals("[AUTH] Login bem-sucedido: m***@umc.br",
                MascaraEmailConverter.mascarar("[AUTH] Login bem-sucedido: maria.souza@umc.br"));
        assertEquals("Convite de a***@teste.com para b***@exemplo.com.br",
                MascaraEmailConverter.mascarar("Convite de ana@teste.com para bruno.lima@exemplo.com.br"));
        assertEquals("Sem e-mail nesta mensagem", MascaraEmailConverter.mascarar("Sem e-mail nesta mensagem"));
    }
}
