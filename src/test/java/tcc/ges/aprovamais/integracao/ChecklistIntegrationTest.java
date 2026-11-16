package tcc.ges.aprovamais.integracao;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.external.EmailService;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
   Checklist de critérios (F6) de ponta a ponta pela API: o coordenador cadastra os critérios da tarefa,
   o orientador marca cada um na correção e o aluno vê o resultado no histórico de versões
*/
@TestPropertySource(properties = "aprova.armazenamento.diretorio=target/uploads-teste")
class ChecklistIntegrationTest extends CoordenacaoTestBase {

    private static final byte[] PDF = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @MockitoBean
    private EmailService emailService;

    private Tarefa relatorio;

    @BeforeEach
    void preparar() {
        Turma turma = criarTurma();
        turma.getCurso().setCoordenador(coordenador);
        TipoEstagio tipo = criarTipoEstagio(turma.getCurso());

        Orientador orientador = criarOrientador(EMAIL_ORIENTADOR, "ORI-001");
        vincularOrientadorNaTurma(orientador, turma);
        criarEstagio(criarMatricula(criarAluno(EMAIL_ALUNO, "RGM-001"), turma), orientador, tipo, StatusEstagio.ATIVO);

        Trilha trilha = new Trilha();
        trilha.setTipoEstagio(tipo);
        trilha.setNome("Trilha Estagiário");
        entityManager.persist(trilha);

        relatorio = new Tarefa();
        relatorio.setTrilha(trilha);
        relatorio.setNome("Relatório Final");
        relatorio.setOrdem(1);
        entityManager.persist(relatorio);
        entityManager.flush();
    }

    @Test
    @DisplayName("Coordenador cadastra os critérios da tarefa; o de outro curso recebe 404")
    void deveCadastrarCriteriosSoNosPropriosCursos() throws Exception {
        criarCriterio("Assinatura do supervisor", 1);

        mockMvc.perform(get("/api/v1/coordenador/trilhas/tarefas/{id}/criterios", relatorio.getId())
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].descricao").value("Assinatura do supervisor"));

        mockMvc.perform(post("/api/v1/coordenador/trilhas/tarefas/{id}/criterios", relatorio.getId())
                        .with(user(EMAIL_OUTRO_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCriterio("Clareza", 2)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Dois critérios com a mesma ordem na mesma tarefa são recusados")
    void naoDeveCadastrarCriterioComOrdemRepetida() throws Exception {
        criarCriterio("Assinatura do supervisor", 1);

        mockMvc.perform(post("/api/v1/coordenador/trilhas/tarefas/{id}/criterios", relatorio.getId())
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCriterio("Descrição das atividades", 1)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Com critério insuficiente só dá para devolver, e o aluno vê o resultado de cada critério")
    void deveDevolverComCriterioInsuficienteEMostrarAoAluno() throws Exception {
        Long assinatura = criarCriterio("Assinatura do supervisor", 1);
        Long atividades = criarCriterio("Descrição das atividades", 2);
        Long entregaId = enviarEntrega();

        // O orientador abre a entrega e recebe os critérios para preencher
        abrir(entregaId)
                .andExpect(jsonPath("$.criterios", hasSize(2)))
                .andExpect(jsonPath("$.criterios[0].descricao").value("Assinatura do supervisor"));

        String checklist = "[" + resposta(assinatura, "INSUFICIENTE", "Faltou a assinatura na página 3") + ","
                + resposta(atividades, "SUFICIENTE", null) + "]";

        avaliar(entregaId, "APROVAR", null, checklist)
                .andExpect(status().isConflict());

        avaliar(entregaId, "DEVOLVER", "Colha a assinatura do supervisor.", checklist)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEVOLVIDA"))
                .andExpect(jsonPath("$.versoes[0].checklist", hasSize(2)));

        // O aluno vê o resultado e a observação de cada critério
        mockMvc.perform(get("/api/v1/aluno/tarefas/{id}/entrega", relatorio.getId())
                        .with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criterios", hasSize(0)))
                .andExpect(jsonPath("$.versoes[0].checklist[0].descricaoCriterio").value("Assinatura do supervisor"))
                .andExpect(jsonPath("$.versoes[0].checklist[0].resultado").value("INSUFICIENTE"))
                .andExpect(jsonPath("$.versoes[0].checklist[0].observacao").value("Faltou a assinatura na página 3"))
                .andExpect(jsonPath("$.versoes[0].checklist[1].resultado").value("SUFICIENTE"));
    }

    @Test
    @DisplayName("Correção com critério sem marcar é recusada")
    void naoDeveCorrigirComCriterioSemMarcar() throws Exception {
        Long assinatura = criarCriterio("Assinatura do supervisor", 1);
        criarCriterio("Descrição das atividades", 2);
        Long entregaId = enviarEntrega();

        avaliar(entregaId, "DEVOLVER", "Faltou a assinatura.", "[" + resposta(assinatura, "INSUFICIENTE", null) + "]")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A versão 2 recebe um checklist novo, e o da versão 1 continua no histórico")
    void deveManterOChecklistDaVersaoAnterior() throws Exception {
        Long assinatura = criarCriterio("Assinatura do supervisor", 1);
        Long entregaId = enviarEntrega();

        avaliar(entregaId, "DEVOLVER", "Colha a assinatura.", "[" + resposta(assinatura, "INSUFICIENTE", null) + "]")
                .andExpect(status().isOk());
        enviarEntrega();
        avaliar(entregaId, "APROVAR", null, "[" + resposta(assinatura, "SUFICIENTE", null) + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADA"))
                .andExpect(jsonPath("$.versoes[0].checklist[0].resultado").value("INSUFICIENTE"))
                .andExpect(jsonPath("$.versoes[1].checklist[0].resultado").value("SUFICIENTE"));
    }

    @Test
    @DisplayName("Critério já usado é desativado, e a correção antiga mantém a descrição da época")
    void deveDesativarCriterioUsadoSemMudarACorrecaoAntiga() throws Exception {
        Long assinatura = criarCriterio("Assinatura do supervisor", 1);
        Long entregaId = enviarEntrega();
        avaliar(entregaId, "DEVOLVER", "Colha a assinatura.", "[" + resposta(assinatura, "INSUFICIENTE", null) + "]")
                .andExpect(status().isOk());

        // O coordenador altera a descrição e depois remove o critério
        mockMvc.perform(put("/api/v1/coordenador/trilhas/criterios/{id}", assinatura)
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCriterio("Assinatura e carimbo da empresa", 1)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/coordenador/trilhas/criterios/{id}", assinatura)
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/coordenador/trilhas/tarefas/{id}/criterios", relatorio.getId())
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR")))
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/aluno/tarefas/{id}/entrega", relatorio.getId())
                        .with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(jsonPath("$.versoes[0].checklist[0].descricaoCriterio").value("Assinatura do supervisor"));
    }

    @Test
    @DisplayName("Tarefa sem entregas é removida junto com os critérios dela")
    void deveRemoverTarefaSemEntregasComOsCriterios() throws Exception {
        criarCriterio("Assinatura do supervisor", 1);

        mockMvc.perform(delete("/api/v1/coordenador/trilhas/tarefas/{id}", relatorio.getId())
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR")))
                .andExpect(status().isNoContent());

        // O flush manda os comandos ao banco agora. Se a tarefa fosse apagada antes dos critérios, o banco recusaria aqui
        entityManager.flush();
    }

    // auxiliares

    // Cadastra um critério na tarefa e devolve o id dele, procurado pela ordem na lista que a API devolve
    private Long criarCriterio(String descricao, int ordem) throws Exception {
        String resposta = mockMvc.perform(post("/api/v1/coordenador/trilhas/tarefas/{id}/criterios", relatorio.getId())
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCriterio(descricao, ordem)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Number> ids = JsonPath.read(resposta, "$[?(@.ordem == " + ordem + ")].id");
        return ids.get(0).longValue();
    }

    private String corpoCriterio(String descricao, int ordem) {
        return """
                {"descricao": "%s", "ordem": %d}
                """.formatted(descricao, ordem);
    }

    private String resposta(Long criterioId, String resultado, String observacao) {
        String observacaoJson = "null";
        if (observacao != null) {
            observacaoJson = "\"" + observacao + "\"";
        }
        return """
                {"criterioId": %d, "resultado": "%s", "observacao": %s}
                """.formatted(criterioId, resultado, observacaoJson);
    }

    // O aluno envia um arquivo para a tarefa e devolvemos o id da entrega
    private Long enviarEntrega() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "relatorio.pdf", "application/octet-stream", PDF);
        String resposta = mockMvc.perform(multipart("/api/v1/aluno/tarefas/{id}/entregas", relatorio.getId())
                        .file(arquivo)
                        .with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(resposta, "$.entregaId")).longValue();
    }

    private ResultActions abrir(Long entregaId) throws Exception {
        return mockMvc.perform(get("/api/v1/orientador/entregas/{id}", entregaId)
                .with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR")));
    }

    private ResultActions avaliar(Long entregaId, String decisao, String feedback, String respostas) throws Exception {
        String feedbackJson = "null";
        if (feedback != null) {
            feedbackJson = "\"" + feedback + "\"";
        }
        String corpo = """
                {"decisao": "%s", "feedback": %s, "respostas": %s}
                """.formatted(decisao, feedbackJson, respostas);
        return mockMvc.perform(post("/api/v1/orientador/entregas/{id}/avaliacao", entregaId)
                .with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR"))
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }
}
