package tcc.ges.aprovamais.integracao;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/*
   Teste de integração do login em duas etapas usado pelos testes de curso e
   turma. Confere o header Set-Cookie: o JWT só pode sair num cookie HttpOnly,
   nunca no corpo, para não ficar acessível a scripts da página
*/
class AutenticacaoIntegrationTest extends ApiIntegrationTestBase {

    @Test
    void verificar2fa_comCodigoValido_deveRetornar200ComCookieJwtHttpOnly() {
        // Arrange: coordenador com 2FA ativo e a primeira etapa do login concluída
        String email = emailUnico("coordenador");
        String segredo = criarCoordenadorCom2fa(email);
        String tokenPreAutenticacao = fazerLogin(email);

        // Act
        ResponseEntity<Map<String, Object>> resposta = verificarSegundoFator(tokenPreAutenticacao, segredo);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE))
                .anySatisfy(cookie -> assertThat(cookie)
                        .startsWith("jwt=")
                        .contains("HttpOnly")
                        .contains("Path=/")
                        .contains("SameSite=Strict"));
        assertThat(resposta.getBody().get("email")).isEqualTo(email);
        assertThat(resposta.getBody().get("perfil")).isEqualTo("COORDENADOR");
        assertThat(resposta.getBody().get("token")).isNull();
    }
}
