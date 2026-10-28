package tcc.ges.aprovamais.integracao;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tcc.ges.aprovamais.entity.TipoEstagio;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Testa a montagem de trilhas e tarefas pela API (RF15, RN32, RN33)
class CoordenacaoTrilhaIntegrationTest extends CoordenacaoTestBase {

    private TipoEstagio tipo;

    @BeforeEach
    void setUp() {
        tipo = criarTipoEstagio(criarCurso("Engenharia de Software", "1105", coordenador));
        entityManager.flush();
    }

    private String trilha() {
        return "{\"tipoEstagioId\": %d, \"nome\": \"Trilha Estagiário\"}".formatted(tipo.getId());
    }

    private String tarefa(String nome, int ordem) {
        return "{\"nome\": \"%s\", \"ordem\": %d}".formatted(nome, ordem);
    }

    @Test
    @DisplayName("Tipo sem trilha responde 204, e depois de criada a trilha um segundo cadastro volta 409")
    void deveCriarUmaTrilhaPorTipo() throws Exception {
        mockMvc.perform(get("/api/v1/coordenador/trilhas").param("tipoEstagioId", tipo.getId().toString())
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR")))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/coordenador/trilhas")
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(trilha()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Trilha Estagiário"));

        mockMvc.perform(post("/api/v1/coordenador/trilhas")
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(trilha()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Tarefas voltam na ordem definida, e a remoção tira a tarefa da trilha (CA02 do RF15)")
    void deveManterAsTarefasNaOrdem() throws Exception {
        String resposta = mockMvc.perform(post("/api/v1/coordenador/trilhas")
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(trilha()))
                .andReturn().getResponse().getContentAsString();
        Integer trilhaId = JsonPath.read(resposta, "$.id");

        // cadastradas fora de ordem de propósito
        for (String[] t : new String[][]{{"Relatório Final", "3"}, {"Termo de Compromisso", "1"}, {"Relatório Parcial", "2"}}) {
            mockMvc.perform(post("/api/v1/coordenador/trilhas/{id}/tarefas", trilhaId)
                            .with(user(EMAIL_COORDENADOR).roles("COORDENADOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(tarefa(t[0], Integer.parseInt(t[1]))))
                    .andExpect(status().isCreated());
        }

        String trilhaCompleta = mockMvc.perform(get("/api/v1/coordenador/trilhas").param("tipoEstagioId", tipo.getId().toString())
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tarefas", hasSize(3)))
                .andExpect(jsonPath("$.tarefas[0].nome").value("Termo de Compromisso"))
                .andExpect(jsonPath("$.tarefas[1].nome").value("Relatório Parcial"))
                .andExpect(jsonPath("$.tarefas[2].nome").value("Relatório Final"))
                .andReturn().getResponse().getContentAsString();
        Integer primeiraTarefaId = JsonPath.read(trilhaCompleta, "$.tarefas[0].id");

        // outro coordenador não remove, o dono remove
        mockMvc.perform(delete("/api/v1/coordenador/trilhas/tarefas/{id}", primeiraTarefaId)
                        .with(user(EMAIL_OUTRO_COORDENADOR).roles("COORDENADOR")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/coordenador/trilhas/tarefas/{id}", primeiraTarefaId)
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/coordenador/trilhas").param("tipoEstagioId", tipo.getId().toString())
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR")))
                .andExpect(jsonPath("$.tarefas", hasSize(2)));
    }

    @Test
    @DisplayName("Link de apoio que não começa com http ou https volta 400")
    void deveRecusarLinkInvalido() throws Exception {
        String resposta = mockMvc.perform(post("/api/v1/coordenador/trilhas")
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(trilha()))
                .andReturn().getResponse().getContentAsString();
        Integer trilhaId = JsonPath.read(resposta, "$.id");

        mockMvc.perform(post("/api/v1/coordenador/trilhas/{id}/tarefas", trilhaId)
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Termo\", \"ordem\": 1, \"linkApoio\": \"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.linkApoio").exists());
    }
}
