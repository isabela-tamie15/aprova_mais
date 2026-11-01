package tcc.ges.aprovamais.integracao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tcc.ges.aprovamais.entity.Turma;
import tcc.ges.aprovamais.entity.enums.Modalidade;
import tcc.ges.aprovamais.entity.enums.Periodo;
import tcc.ges.aprovamais.repository.TurmaRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/*
   Testes de integração do TurmaController (/api/v1/coordenador/turmas) por
   HTTP. Cada teste parte de um curso criado pela própria API e cobre a
   relação turma com curso, a turma só existe dentro de um curso do coordenador
*/
class TurmaControllerIntegrationTest extends ApiIntegrationTestBase {

    private static final String BASE_PATH = "/api/v1/coordenador/turmas";

    @Autowired
    private TurmaRepository turmaRepository;

    private String emailCoordenador;
    private String tokenCoordenador;
    private Long cursoId;

    @BeforeEach
    void prepararCoordenadorECurso() {
        emailCoordenador = emailUnico("coordenador");
        tokenCoordenador = criarCoordenadorELogar(emailCoordenador);
        String codigo = codigoUnico();
        cursoId = criarCurso(tokenCoordenador, "Curso de Turmas " + codigo, "TU-" + codigo);
    }

    //GET /turmas?cursoId=

    @Test
    void listar_comTurmasCadastradas_deveRetornar200OrdenadoPorSemestreMaisRecente() {
        // Arrange
        criarTurma(tokenCoordenador, cursoId, "2026/1", "7A");
        criarTurma(tokenCoordenador, cursoId, "2026/2", "8B");

        // Act
        ResponseEntity<List<Map<String, Object>>> resposta = client.get()
                .uri(BASE_PATH + "?cursoId={cursoId}", cursoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCoordenador)
                .retrieve()
                .toEntity(LISTA);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).hasSize(2);
        assertThat(resposta.getBody()).extracting(turma -> turma.get("semestre"))
                .containsExactly("2026/2", "2026/1");
        assertThat(resposta.getBody()).extracting(turma -> turma.get("identificador"))
                .containsExactly("8B", "7A");
    }

    @Test
    void listar_comCursoDeOutroCoordenador_deveRetornar404ComMensagem() {
        // Arrange
        String tokenOutro = criarCoordenadorELogar(emailUnico("outro"));
        String codigo = codigoUnico();
        Long cursoAlheio = criarCurso(tokenOutro, "Curso Alheio " + codigo, "AL-" + codigo);
        criarTurma(tokenOutro, cursoAlheio, "2026/2", "9C");

        // Act
        ResponseEntity<Map<String, Object>> resposta = client.get()
                .uri(BASE_PATH + "?cursoId={cursoId}", cursoAlheio)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCoordenador)
                .retrieve()
                .toEntity(MAPA);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Curso não encontrado");
    }

    //POST /turmas

    @Test
    void criar_comDadosValidos_deveRetornar201ComCursoEPersistirNoBanco() {
        // identificador em minusculo e com espaços, para conferir a normalização
        ResponseEntity<Map<String, Object>> resposta = client.post().uri(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCoordenador)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildTurmaRequest(cursoId, "2026/2", " 8b ", "NOTURNO", "PRESENCIAL"))
                .retrieve()
                .toEntity(MAPA);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long id = idDe(resposta.getBody());
        assertThat(((Number) resposta.getBody().get("cursoId")).longValue()).isEqualTo(cursoId);
        assertThat(resposta.getBody().get("identificador")).isEqualTo("8B");
        assertThat(resposta.getBody().get("semestre")).isEqualTo("2026/2");
        assertThat(resposta.getBody().get("periodo")).isEqualTo("NOTURNO");
        assertThat(turmaRepository.findByIdAndCursoCoordenadorEmail(id, emailCoordenador)).get()
                .extracting(Turma::getIdentificador)
                .isEqualTo("8B");
    }

    @Test
    void criar_comTurmaRepetidaNoSemestre_deveRetornar409ComMensagem() {
        // Arrange
        criarTurma(tokenCoordenador, cursoId, "2026/2", "8B");

        // mesmo identificador, só muda maiúscula/minúscula
        ResponseEntity<Map<String, Object>> resposta = client.post().uri(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCoordenador)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildTurmaRequest(cursoId, "2026/2", "8b", "MATUTINO", "EAD"))
                .retrieve()
                .toEntity(MAPA);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Já existe a turma 8B neste semestre do curso");
        assertThat(turmaRepository.findByCursoIdOrderBySemestreDescIdentificadorAsc(cursoId)).hasSize(1);
    }

    @Test
    void criar_comSemestreForaDoFormato_deveRetornar400ComMensagemDoCampo() {
        // Act
        ResponseEntity<Map<String, Object>> resposta = client.post().uri(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCoordenador)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildTurmaRequest(cursoId, "2026-2", "8B", "NOTURNO", "PRESENCIAL"))
                .retrieve()
                .toEntity(MAPA);

        // Assert
        String mensagemEsperada = "Informe o semestre no formato AAAA/1 ou AAAA/2";
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo(mensagemEsperada);
        assertThat(camposDe(resposta.getBody())).containsEntry("semestre", mensagemEsperada);
    }

    @Test
    void criar_emCursoDeOutroCoordenador_deveRetornar404ENaoGravarTurma() {
        // Arrange
        String tokenOutro = criarCoordenadorELogar(emailUnico("outro"));
        String codigo = codigoUnico();
        Long cursoAlheio = criarCurso(tokenOutro, "Curso Alheio " + codigo, "AL-" + codigo);

        // Act
        ResponseEntity<Map<String, Object>> resposta = client.post().uri(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCoordenador)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildTurmaRequest(cursoAlheio, "2026/2", "8B", "NOTURNO", "PRESENCIAL"))
                .retrieve()
                .toEntity(MAPA);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Curso não encontrado");
        assertThat(turmaRepository.findByCursoIdOrderBySemestreDescIdentificadorAsc(cursoAlheio)).isEmpty();
    }

    // PUT /turmas/{id}

    @Test
    void atualizar_comNovoPeriodoEModalidade_deveRetornar200EGravarAlteracao() {
        // Arrange
        Long id = criarTurma(tokenCoordenador, cursoId, "2026/2", "8B");

        // Act
        ResponseEntity<Map<String, Object>> resposta = client.put().uri(BASE_PATH + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCoordenador)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildTurmaRequest(cursoId, "2026/2", "8B", "MATUTINO", "HIBRIDO"))
                .retrieve()
                .toEntity(MAPA);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().get("periodo")).isEqualTo("MATUTINO");
        assertThat(resposta.getBody().get("modalidade")).isEqualTo("HIBRIDO");
        Turma gravada = turmaRepository.findByIdAndCursoCoordenadorEmail(id, emailCoordenador).orElseThrow();
        assertThat(gravada.getPeriodo()).isEqualTo(Periodo.MATUTINO);
        assertThat(gravada.getModalidade()).isEqualTo(Modalidade.HIBRIDO);
    }

    @Test
    void atualizar_trocandoTurmaDeCurso_deveRetornar409ComMensagem() {
        // Arrange
        Long id = criarTurma(tokenCoordenador, cursoId, "2026/2", "8B");
        String codigo = codigoUnico();
        Long outroCurso = criarCurso(tokenCoordenador, "Outro Curso " + codigo, "OC-" + codigo);

        // Act
        ResponseEntity<Map<String, Object>> resposta = client.put().uri(BASE_PATH + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCoordenador)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildTurmaRequest(outroCurso, "2026/2", "8B", "NOTURNO", "PRESENCIAL"))
                .retrieve()
                .toEntity(MAPA);

        // Assert: a turma continua no curso original
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Uma turma não pode mudar de curso");
        assertThat(turmaRepository.findByCursoIdOrderBySemestreDescIdentificadorAsc(outroCurso)).isEmpty();
    }

    // Fluxo entre os dois recursos
    @Test
    void fluxo_criarDoisCursosComTurmas_deveListarCadaTurmaSomenteNoSeuCurso() {
        // segundo curso e uma turma em cada um, tudo pela API
        String codigo = codigoUnico();
        Long segundoCurso = criarCurso(tokenCoordenador, "Segundo Curso " + codigo, "SC-" + codigo);
        criarTurma(tokenCoordenador, cursoId, "2026/2", "PRIMEIRO");
        criarTurma(tokenCoordenador, segundoCurso, "2026/2", "SEGUNDO");

        // Act
        ResponseEntity<List<Map<String, Object>>> resposta = client.get()
                .uri(BASE_PATH + "?cursoId={cursoId}", segundoCurso)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCoordenador)
                .retrieve()
                .toEntity(LISTA);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).hasSize(1);
        assertThat(resposta.getBody().get(0).get("identificador")).isEqualTo("SEGUNDO");
        assertThat(resposta.getBody().get(0).get("nomeCurso")).isEqualTo("Segundo Curso " + codigo);
    }
}
