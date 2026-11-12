package tcc.ges.aprovamais.integracao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tcc.ges.aprovamais.entity.Curso;
import tcc.ges.aprovamais.repository.CursoRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/*
   Testes de integração do CursoController (/api/v1/coordenador/cursos) por
   HTTP real. Cobre os três endpoints (listar, cadastrar, alterar), os erros
   de validação, de unicidade e de posse do curso (RN21), e a segurança da rota
*/
class CursoControllerIntegrationTest extends ApiIntegrationTestBase {

    private static final String BASE_PATH = "/api/v1/coordenador/cursos";

    @Autowired
    private CursoRepository cursoRepository;

    private String emailCoordenador;
    private String tokenCoordenador;

    @BeforeEach
    void autenticarCoordenador() {
        emailCoordenador = emailUnico("coordenador");
        tokenCoordenador = criarCoordenadorELogar(emailCoordenador);
    }

    // ===== GET /cursos =====

    @Test
    void listar_comCursosDeDoisCoordenadores_deveRetornar200SomenteComOsDoCoordenadorLogado() {
        // Arrange
        String proprio = codigoUnico();
        criarCurso(tokenCoordenador, "Curso Proprio " + proprio, "P-" + proprio);
        String tokenOutro = criarCoordenadorELogar(emailUnico("outro"));
        String alheio = codigoUnico();
        criarCurso(tokenOutro, "Curso Alheio " + alheio, "A-" + alheio);

        // Act
        ResponseEntity<List<Map<String, Object>>> resposta = client.get().uri(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCoordenador)
                .retrieve()
                .toEntity(LISTA);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).hasSize(1);
        assertThat(resposta.getBody().get(0).get("nome")).isEqualTo("Curso Proprio " + proprio);
        assertThat(resposta.getBody().get(0).get("codigoMec")).isEqualTo("P-" + proprio);
    }

    @Test
    void listar_semToken_deveRetornar401ComMensagem() {
        // Act
        ResponseEntity<Map<String, Object>> resposta = client.get().uri(BASE_PATH)
                .retrieve()
                .toEntity(MAPA);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resposta.getBody().get("status")).isEqualTo(401);
        assertThat((String) resposta.getBody().get("mensagem")).contains("Autenticação necessária");
    }

    @Test
    void listar_comTokenDeAluno_deveRetornar403() {
        // Arrange
        String tokenAluno = criarAlunoELogar(emailUnico("aluno"));

        // Act
        ResponseEntity<Void> resposta = client.get().uri(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAluno)
                .retrieve()
                .toBodilessEntity();

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listar_comTokenDeCoordenadorSemCursos_deveRetornar200ComListaVazia() {
        // Act
        ResponseEntity<List<Map<String, Object>>> resposta = client.get().uri(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCoordenador)
                .retrieve()
                .toEntity(LISTA);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isEmpty();
    }

    // ===== POST /cursos =====

    @Test
    void criar_comDadosValidos_deveRetornar201ComIdEPersistirNoBanco() {
        // Arrange
        String codigo = codigoUnico();

        // Act
        ResponseEntity<Map<String, Object>> resposta = client.post().uri(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCoordenador)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildCursoRequest("Engenharia de Software " + codigo, "ES-" + codigo))
                .retrieve()
                .toEntity(MAPA);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long id = idDe(resposta.getBody());
        assertThat(id).isNotNull();
        assertThat(resposta.getBody().get("nome")).isEqualTo("Engenharia de Software " + codigo);
        assertThat(resposta.getBody().get("codigoMec")).isEqualTo("ES-" + codigo);
        // Persistiu e ficou vinculado ao coordenador que cadastrou (RN20)
        assertThat(cursoRepository.findByIdAndCoordenadorEmail(id, emailCoordenador)).isPresent();
    }

    @Test
    void criar_comCodigoMecDuplicado_deveRetornar409ComMensagem() {
        // Arrange
        String codigoMec = "DUP-" + codigoUnico();
        criarCurso(tokenCoordenador, "Curso Original", codigoMec);

        // Act
        ResponseEntity<Map<String, Object>> resposta = client.post().uri(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCoordenador)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildCursoRequest("Curso Duplicado", codigoMec))
                .retrieve()
                .toEntity(MAPA);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Já existe um curso com esse código e-MEC");
    }

    @Test
    void criar_comCodigoMecComCaracteresInvalidos_deveRetornar400ComMensagemDoCampo() {
        // Act
        ResponseEntity<Map<String, Object>> resposta = client.post().uri(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCoordenador)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildCursoRequest("Curso Invalido", "MEC 123!"))
                .retrieve()
                .toEntity(MAPA);

        // Assert
        String mensagemEsperada = "O código e-MEC deve ter apenas letras, números e hífen";
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo(mensagemEsperada);
        assertThat(camposDe(resposta.getBody())).containsEntry("codigoMec", mensagemEsperada);
    }

    // ===== PUT /cursos/{id} =====

    @Test
    void atualizar_comDadosValidos_deveRetornar200EGravarNovoNome() {
        // Arrange
        String codigoMec = "UPD-" + codigoUnico();
        Long id = criarCurso(tokenCoordenador, "Nome Antigo", codigoMec);

        // Act
        ResponseEntity<Map<String, Object>> resposta = client.put().uri(BASE_PATH + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCoordenador)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildCursoRequest("Nome Novo", codigoMec))
                .retrieve()
                .toEntity(MAPA);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(idDe(resposta.getBody())).isEqualTo(id);
        assertThat(resposta.getBody().get("nome")).isEqualTo("Nome Novo");
        assertThat(cursoRepository.findById(id)).get()
                .extracting(Curso::getNome)
                .isEqualTo("Nome Novo");
    }

    @Test
    void atualizar_cursoDeOutroCoordenador_deveRetornar404ComMensagemENaoAlterar() {
        // Arrange
        String tokenOutro = criarCoordenadorELogar(emailUnico("outro"));
        String codigoMec = "OUT-" + codigoUnico();
        Long idAlheio = criarCurso(tokenOutro, "Curso Alheio", codigoMec);

        // Act
        ResponseEntity<Map<String, Object>> resposta = client.put().uri(BASE_PATH + "/" + idAlheio)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCoordenador)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildCursoRequest("Tentativa de Alteracao", codigoMec))
                .retrieve()
                .toEntity(MAPA);

        // Assert: mesmo 404 de um id inexistente (RN21), e o curso continua como estava
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Curso não encontrado");
        assertThat(cursoRepository.findById(idAlheio)).get()
                .extracting(Curso::getNome)
                .isEqualTo("Curso Alheio");
    }
}
