package tcc.ges.aprovamais.integracao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.repository.EstagioRepository;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


class EstagioControllerIntegrationTest extends IntegracaoTestBase {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private EstagioRepository estagioRepository;
    private MockMvc mockMvc;
    private Matricula matricula;
    private Orientador orientador;
    private TipoEstagio tipoEstagio;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();


        Turma turma = criarTurma();
        tipoEstagio = criarTipoEstagio(turma.getCurso());
        orientador = criarOrientador(EMAIL_ORIENTADOR, "ORI-001");
        vincularOrientadorNaTurma(orientador, turma);
        matricula = criarMatricula(criarAluno(EMAIL_ALUNO, "RGM-001"), turma);


    }

    @Test
    @DisplayName("POST /aprovar retorna 200 e o estágio volta como ativo")
    void deveRetornar200AoAprovarEstagioPendente() throws Exception {
        Estagio estagio = criarEstagio(matricula, orientador, tipoEstagio, StatusEstagio.PENDENTE);


        mockMvc.perform(post("/api/v1/orientador/validacoes/{id}/aprovar", estagio.getId()).with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(estagio.getId()))
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.nomeEmpresa").value("Empresa Teste"));


    }

    @Test
    @DisplayName("POST /aprovar em estagio que não está pendente retorna 409 com a mensagem de erro")
    void deveRetornar409AoAprovarEstagioQueNaoEstaPendente() throws Exception {
        Estagio estagio = criarEstagio(matricula, orientador, tipoEstagio, StatusEstagio.ATIVO);


        mockMvc.perform(post("/api/v1/orientador/validacoes/{id}/aprovar", estagio.getId()).with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensagem", containsString("pendentes podem ser aprovados")));

    }

    @Test
    @DisplayName("Aluno cadastra estagio pela API e ele aparece na lista do orientador")
    void deveCadastrarEstagioEExibirNaListaDoOrientador() throws Exception {
        String corpo = """
                {
                  "dataInicio": "2026-10-01",
                  "tipoEstagioId": %d,
                  "nomeEmpresa": "Empresa Integracao"
                }
                """.formatted(tipoEstagio.getId());

        // o aluno envia o estagio
        mockMvc.perform(post("/api/v1/aluno/estagio")
                        .with(user(EMAIL_ALUNO).roles("ALUNO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.nomeOrientador").value("Orientador Teste"));



        // ficou gravado no banco como pendente
        Optional<Estagio> gravado = estagioRepository.findByMatriculaId(matricula.getId());
        assertTrue(gravado.isPresent());
        assertEquals(StatusEstagio.PENDENTE, gravado.get().getStatus());




        // o orientador da turma enxerga a solicitação
        mockMvc.perform(get("/api/v1/orientador/validacoes").with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nomeEmpresa").value("Empresa Integracao"))
                .andExpect(jsonPath("$[0].status").value("PENDENTE"));
    }
}