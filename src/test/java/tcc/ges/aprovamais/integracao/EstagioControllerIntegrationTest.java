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
import tcc.ges.aprovamais.entity.enums.MotivoEncerramento;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.repository.EstagioRepository;

import java.util.List;

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
        List<Estagio> gravados = estagioRepository.findByMatriculaIdOrderByIdAsc(matricula.getId());
        assertEquals(1, gravados.size());
        assertEquals(StatusEstagio.PENDENTE, gravados.get(0).getStatus());




        // o orientador da turma enxerga a solicitação
        mockMvc.perform(get("/api/v1/orientador/validacoes").with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nomeEmpresa").value("Empresa Integracao"))
                .andExpect(jsonPath("$[0].status").value("PENDENTE"));
    }

    @Test
    @DisplayName("GET /tipos devolve só os tipos do curso do aluno e barra cadastro com tipo de outro curso")
    void deveFiltrarTiposPeloCursoDoAluno() throws Exception {
        // um segundo curso com um tipo próprio, que o aluno não pode ver nem usar
        Curso outroCurso = new Curso();
        outroCurso.setNome("Administração");
        outroCurso.setCodigoMec("MEC-TESTE-002");
        entityManager.persist(outroCurso);
        TipoEstagio tipoDeOutroCurso = criarTipoEstagio(outroCurso);
        tipoDeOutroCurso.setNome("CLT Administração");
        entityManager.flush();

        mockMvc.perform(get("/api/v1/aluno/estagio/tipos").with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(tipoEstagio.getId()));

        String corpo = """
                {
                  "dataInicio": "2026-10-01",
                  "tipoEstagioId": %d,
                  "nomeEmpresa": "Empresa Integracao"
                }
                """.formatted(tipoDeOutroCurso.getId());

        mockMvc.perform(post("/api/v1/aluno/estagio")
                        .with(user(EMAIL_ALUNO).roles("ALUNO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isNotFound());

        assertTrue(estagioRepository.findByMatriculaIdOrderByIdAsc(matricula.getId()).isEmpty());
    }

    @Test
    @DisplayName("Rejeição, reenvio e aprovação ficam no histórico, com a justificativa preservada")
    void deveManterHistoricoDeValidacoes() throws Exception {
        Estagio estagio = criarEstagio(matricula, orientador, tipoEstagio, StatusEstagio.PENDENTE);

        // orientador rejeita
        mockMvc.perform(post("/api/v1/orientador/validacoes/{id}/rejeitar", estagio.getId())
                        .with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificativa\": \"Data não confere com o termo\"}"))
                .andExpect(status().isOk());

        // aluno corrige e reenvia
        String corpo = """
                {
                  "dataInicio": "2026-08-03",
                  "tipoEstagioId": %d,
                  "nomeEmpresa": "Empresa Teste"
                }
                """.formatted(tipoEstagio.getId());
        mockMvc.perform(post("/api/v1/aluno/estagio")
                        .with(user(EMAIL_ALUNO).roles("ALUNO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk());

        // orientador aprova sem corpo, vale a data do aluno
        mockMvc.perform(post("/api/v1/orientador/validacoes/{id}/aprovar", estagio.getId())
                        .with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataInicio").value("2026-08-03"));

        // o aluno vê as duas decisões, a rejeição com a justificativa
        mockMvc.perform(get("/api/v1/aluno/estagio/historico").with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].aprovado").value(false))
                .andExpect(jsonPath("$[0].justificativa").value("Data não confere com o termo"))
                .andExpect(jsonPath("$[0].nomeOrientador").value("Orientador Teste"))
                .andExpect(jsonPath("$[1].aprovado").value(true));

        // outro orientador não enxerga o histórico desse estágio
        criarOrientador("outro@teste.com", "ORI-002");
        mockMvc.perform(get("/api/v1/orientador/validacoes/{id}/historico", estagio.getId())
                        .with(user("outro@teste.com").roles("ORIENTADOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Estágio encerrado fica no histórico e a matrícula aceita um novo, mas não reenvio de um ATIVO")
    void deveManterHistoricoDeEstagiosDaMatricula() throws Exception {
        // estágio anterior já encerrado, por exemplo numa troca de modalidade
        Estagio anterior = criarEstagio(matricula, orientador, tipoEstagio, StatusEstagio.ENCERRADO);
        anterior.setMotivoEncerramento(MotivoEncerramento.TROCA_MODALIDADE);
        entityManager.flush();

        String corpo = """
                {
                  "dataInicio": "2026-10-01",
                  "tipoEstagioId": %d,
                  "nomeEmpresa": "Empresa Nova"
                }
                """.formatted(tipoEstagio.getId());

        // com o anterior encerrado, o aluno consegue abrir um novo estágio
        mockMvc.perform(post("/api/v1/aluno/estagio")
                        .with(user(EMAIL_ALUNO).roles("ALUNO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDENTE"));

        // o estágio atual é o novo, e a lista completa traz os dois
        mockMvc.perform(get("/api/v1/aluno/estagio").with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeEmpresa").value("Empresa Nova"));
        mockMvc.perform(get("/api/v1/aluno/estagio/todos").with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status").value("ENCERRADO"))
                .andExpect(jsonPath("$[0].motivoEncerramento").value("TROCA_MODALIDADE"))
                .andExpect(jsonPath("$[1].status").value("PENDENTE"));

        // depois de aprovado, o reenvio é recusado com 409
        Estagio novo = estagioRepository.findByMatriculaIdOrderByIdAsc(matricula.getId()).get(1);
        mockMvc.perform(post("/api/v1/orientador/validacoes/{id}/aprovar", novo.getId())
                        .with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/aluno/estagio")
                        .with(user(EMAIL_ALUNO).roles("ALUNO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem", containsString("troca de modalidade")));
    }

    @Test
    @DisplayName("Formulário inválido volta 400 com a mensagem e o erro de cada campo")
    void deveRetornar400ComErrosPorCampo() throws Exception {
        String corpo = """
                {
                  "dataInicio": "2099-01-01",
                  "tipoEstagioId": %d,
                  "nomeEmpresa": " "
                }
                """.formatted(tipoEstagio.getId());

        mockMvc.perform(post("/api/v1/aluno/estagio")
                        .with(user(EMAIL_ALUNO).roles("ALUNO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").isNotEmpty())
                .andExpect(jsonPath("$.campos.nomeEmpresa").isNotEmpty())
                .andExpect(jsonPath("$.campos.dataInicio")
                        .value("A data de início não pode ser mais de 1 ano no futuro"));

        assertTrue(estagioRepository.findByMatriculaIdOrderByIdAsc(matricula.getId()).isEmpty());
    }

    @Test
    @DisplayName("Data fora do formato volta 400 com mensagem clara, e não 500")
    void deveRetornar400QuandoJsonInvalido() throws Exception {
        String corpo = """
                {
                  "dataInicio": "31/02/2026",
                  "tipoEstagioId": %d,
                  "nomeEmpresa": "Empresa Teste"
                }
                """.formatted(tipoEstagio.getId());

        mockMvc.perform(post("/api/v1/aluno/estagio")
                        .with(user(EMAIL_ALUNO).roles("ALUNO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("formato inválido")));
    }
}
