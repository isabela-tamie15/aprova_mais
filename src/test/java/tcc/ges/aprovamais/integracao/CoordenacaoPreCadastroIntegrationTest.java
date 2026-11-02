package tcc.ges.aprovamais.integracao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tcc.ges.aprovamais.entity.Curso;
import tcc.ges.aprovamais.entity.Turma;
import tcc.ges.aprovamais.entity.enums.Modalidade;
import tcc.ges.aprovamais.entity.enums.Periodo;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Testa a lista de pré-cadastro pela API, com o banco H2 do perfil de testes
class CoordenacaoPreCadastroIntegrationTest extends CoordenacaoTestBase {

    private static final String URL = "/api/v1/coordenador/pre-cadastros";

    private Turma turma;
    private Turma turmaDoOutro;

    @BeforeEach
    void prepararTurmas() {
        turma = criarTurmaDoCurso(criarCurso("Engenharia de Software", "1105", coordenador));
        turmaDoOutro = criarTurmaDoCurso(criarCurso("Direito", "2201", outroCoordenador));
    }

    private Turma criarTurmaDoCurso(Curso curso) {
        Turma nova = new Turma();
        nova.setCurso(curso);
        nova.setSemestre("2026/2");
        nova.setIdentificador("8B");
        nova.setPeriodo(Periodo.NOTURNO);
        nova.setModalidade(Modalidade.PRESENCIAL);
        entityManager.persist(nova);
        entityManager.flush();
        return nova;
    }

    private String corpoAluno(String email, String rgm, Long turmaId) {
        return "{\"nome\":\"Maria Souza\",\"email\":\"" + email + "\",\"perfil\":\"ALUNO\","
                + "\"identificadorInstitucional\":\"" + rgm + "\",\"turmaId\":" + turmaId + "}";
    }

    private ResultActions incluir(String corpo, String emailCoordenador) throws Exception {
        return mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo)
                .with(user(emailCoordenador).roles("COORDENADOR")));
    }

    @Test
    @DisplayName("Coordenador inclui aluno na turma do próprio curso")
    void deveIncluirAluno() throws Exception {
        incluir(corpoAluno("maria@teste.com", "11230000001", turma.getId()), EMAIL_COORDENADOR)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descricaoTurma").value("2026/2 - 8B"))
                .andExpect(jsonPath("$.situacao").value("AGUARDANDO_CONVITE"));
    }

    @Test
    @DisplayName("CA01: aluno sem turma volta 400 com o erro no campo da turma")
    void deveRecusarAlunoSemTurma() throws Exception {
        incluir(corpoAluno("maria@teste.com", "11230000001", null), EMAIL_COORDENADOR)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.turmaId").value("Escolha a turma do aluno"));
    }

    @Test
    @DisplayName("CA02: e-mail repetido volta 409")
    void deveRecusarEmailRepetido() throws Exception {
        incluir(corpoAluno("maria@teste.com", "11230000001", turma.getId()), EMAIL_COORDENADOR)
                .andExpect(status().isCreated());
        incluir(corpoAluno("MARIA@teste.com", "11230000002", turma.getId()), EMAIL_COORDENADOR)
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("CA03: cada coordenador lista só os próprios pré-cadastros")
    void listaSoOsProprios() throws Exception {
        incluir(corpoAluno("maria@teste.com", "11230000001", turma.getId()), EMAIL_COORDENADOR)
                .andExpect(status().isCreated());
        incluir(corpoAluno("joao@teste.com", "11230000002", turmaDoOutro.getId()), EMAIL_OUTRO_COORDENADOR).andExpect(status().isCreated());

        mockMvc.perform(get(URL).with(user(EMAIL_COORDENADOR).roles("COORDENADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].email").value("maria@teste.com"));
    }

    @Test
    @DisplayName("RN21: turma de outro coordenador volta 404")
    void turmaDeOutroCoordenadorVolta404() throws Exception {
        incluir(corpoAluno("maria@teste.com", "11230000001", turmaDoOutro.getId()), EMAIL_COORDENADOR)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Outro perfil não acessa o pré-cadastro")
    void outroPerfilRecebe403() throws Exception {
        mockMvc.perform(get(URL).with(user("secretaria@teste.com").roles("SECRETARIA"))).andExpect(status().isForbidden());

    }
}
