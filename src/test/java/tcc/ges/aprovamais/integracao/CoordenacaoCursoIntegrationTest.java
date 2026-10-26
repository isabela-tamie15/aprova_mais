package tcc.ges.aprovamais.integracao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tcc.ges.aprovamais.entity.Curso;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Testa o cadastro de cursos pela API, com o banco H2 do perfil de testes (RF11, RN19, RN20, RN21)
class CoordenacaoCursoIntegrationTest extends CoordenacaoTestBase {

    @Test
    @DisplayName("Coordenador cadastra cursos e a lista traz só os dele")
    void deveListarApenasOsCursosDoCoordenador() throws Exception {
        criarCurso("Curso de outro", "OUTRO-1", null);

        mockMvc.perform(post("/api/v1/coordenador/cursos")
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Engenharia de Software\", \"codigoMec\": \"1105\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoMec").value("1105"));

        mockMvc.perform(get("/api/v1/coordenador/cursos").with(user(EMAIL_COORDENADOR).roles("COORDENADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nome").value("Engenharia de Software"));

        mockMvc.perform(get("/api/v1/coordenador/cursos").with(user(EMAIL_OUTRO_COORDENADOR).roles("COORDENADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Código e-MEC repetido volta 409, mesmo vindo de outro coordenador")
    void deveRecusarCodigoMecRepetido() throws Exception {
        criarCurso("Engenharia de Software", "1105", coordenador);

        mockMvc.perform(post("/api/v1/coordenador/cursos")
                        .with(user(EMAIL_OUTRO_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Cópia\", \"codigoMec\": \"1105\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem", containsString("código e-MEC")));
    }

    @Test
    @DisplayName("Alterar curso de outro coordenador volta 404")
    void deveResponder404AoAlterarCursoDeOutroCoordenador() throws Exception {
        Curso curso = criarCurso("Engenharia de Software", "1105", coordenador);

        mockMvc.perform(put("/api/v1/coordenador/cursos/{id}", curso.getId())
                        .with(user(EMAIL_OUTRO_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Tentativa\", \"codigoMec\": \"1105\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Formulário inválido volta 400 com o erro de cada campo")
    void deveValidarOsCampos() throws Exception {
        mockMvc.perform(post("/api/v1/coordenador/cursos")
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"A\", \"codigoMec\": \"12 3\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nome").exists())
                .andExpect(jsonPath("$.campos.codigoMec").exists());
    }

    @Test
    @DisplayName("Outro perfil não acessa a API nem a página de cursos")
    void deveBloquearOutrosPerfis() throws Exception {
        mockMvc.perform(get("/api/v1/coordenador/cursos").with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/coordenador/cursos").with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(status().isForbidden());
    }
}
