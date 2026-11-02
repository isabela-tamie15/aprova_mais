package tcc.ges.aprovamais.integracao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tcc.ges.aprovamais.entity.Curso;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Testa o cadastro de turmas pela API (RF12, RN21, RN22)
class CoordenacaoTurmaIntegrationTest extends CoordenacaoTestBase {

    private Curso curso;

    @BeforeEach
    void setUp() {
        curso = criarCurso("Engenharia de Software", "1105", coordenador);
    }

    private String corpo(Long cursoId, String semestre) {
        return """
                {
                  "cursoId": %d,
                  "semestre": "%s",
                  "identificador": "8B",
                  "periodo": "NOTURNO",
                  "modalidade": "PRESENCIAL",
                  "dataEncerramentoEntregas": "2026-12-05"
                }
                """.formatted(cursoId, semestre);
    }

    @Test
    @DisplayName("Coordenador cadastra uma turma e ela aparece na lista do curso")
    void deveCadastrarEListarTurma() throws Exception {
        mockMvc.perform(post("/api/v1/coordenador/turmas")
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(curso.getId(), "2026/2")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.identificador").value("8B"));

        mockMvc.perform(get("/api/v1/coordenador/turmas").param("cursoId", curso.getId().toString())
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].semestre").value("2026/2"));
    }

    @Test
    @DisplayName("Turma em curso de outro coordenador volta 404 (CA01 do RF12)")
    void deveResponder404ParaCursoDeOutroCoordenador() throws Exception {
        mockMvc.perform(post("/api/v1/coordenador/turmas")
                        .with(user(EMAIL_OUTRO_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(curso.getId(), "2026/2")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/coordenador/turmas").param("cursoId", curso.getId().toString())
                        .with(user(EMAIL_OUTRO_COORDENADOR).roles("COORDENADOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Turma sem semestre volta 400 no campo (CA02 do RF12)")
    void deveRecusarTurmaSemSemestre() throws Exception {
        mockMvc.perform(post("/api/v1/coordenador/turmas")
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(curso.getId(), "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.semestre").exists());
    }

    @Test
    @DisplayName("Data de encerramento das entregas é gravada e volta na lista de turmas (F2)")
    void deveGravarDataDeEncerramentoDasEntregas() throws Exception {
        mockMvc.perform(post("/api/v1/coordenador/turmas")
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(curso.getId(), "2026/2")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataEncerramentoEntregas").value("2026-12-05"));

        mockMvc.perform(get("/api/v1/coordenador/turmas").param("cursoId", curso.getId().toString())
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dataEncerramentoEntregas").value("2026-12-05"));
    }

    @Test
    @DisplayName("Turma sem data de encerramento das entregas é aceita e volta com a data vazia (F2)")
    void deveAceitarTurmaSemDataDeEncerramentoDasEntregas() throws Exception {
        String corpoSemData = """
                {
                  "cursoId": %d,
                  "semestre": "2026/2",
                  "identificador": "8B",
                  "periodo": "NOTURNO",
                  "modalidade": "PRESENCIAL"
                }
                """.formatted(curso.getId());

        mockMvc.perform(post("/api/v1/coordenador/turmas")
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoSemData))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataEncerramentoEntregas").value(nullValue()));
    }
}
