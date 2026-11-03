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
import tcc.ges.aprovamais.service.EmailService;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


//Entregas de ponta a ponta pela API o aluno envia, o orientador abre e avalia, o aluno manda nova versão
@TestPropertySource(properties = "aprova.armazenamento.diretorio=target/uploads-teste")
class EntregaIntegrationTest extends CoordenacaoTestBase {

    private static final String EMAIL_OUTRO_ORIENTADOR = "outro.orientador@teste.com";
    private static final String EMAIL_OUTRO_ALUNO = "outro.aluno@teste.com";
    private static final byte[] PDF = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @MockitoBean
    private EmailService emailService;

    private Tarefa relatorio;
    private Tarefa tarefaDeOutraTrilha;

    @BeforeEach
    void preparar() {
        Turma turma = criarTurma();
        turma.getCurso().setCoordenador(coordenador);
        TipoEstagio tipo = criarTipoEstagio(turma.getCurso());

        Orientador orientador = criarOrientador(EMAIL_ORIENTADOR, "ORI-001");
        criarOrientador(EMAIL_OUTRO_ORIENTADOR, "ORI-002");
        vincularOrientadorNaTurma(orientador, turma);
        criarEstagio(criarMatricula(criarAluno(EMAIL_ALUNO, "RGM-001"), turma), orientador, tipo, StatusEstagio.ATIVO);
        criarEstagio(criarMatricula(criarAluno(EMAIL_OUTRO_ALUNO, "RGM-002"), turma), orientador, tipo, StatusEstagio.ATIVO);

        relatorio = criarTarefa(criarTrilha(tipo), "Relatório Parcial");

        TipoEstagio outroTipo = criarTipoEstagio(turma.getCurso());
        outroTipo.setNome("CLT");
        tarefaDeOutraTrilha = criarTarefa(criarTrilha(outroTipo), "Plano de Negócio");
        entityManager.flush();
    }

    // RF33

    @Test
    @DisplayName("Aluno envia o arquivo da tarefa e a entrega fica ENVIADA na versão 1 (RF33)")
    void deveEnviarAPrimeiraVersao() throws Exception {
        enviar(EMAIL_ALUNO, "relatorio.pdf", PDF)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ENVIADA"))
                .andExpect(jsonPath("$.podeEnviar").value(false))
                .andExpect(jsonPath("$.versoes", hasSize(1)))
                .andExpect(jsonPath("$.versoes[0].numero").value(1))
                .andExpect(jsonPath("$.versoes[0].nomeArquivo").value("relatorio.pdf"))
                .andExpect(jsonPath("$.versoes[0].comentarioAluno").value("Primeira versão"));
    }

    @Test
    @DisplayName("Arquivo .exe é recusado e a mensagem lista os formatos aceitos (CA02 do RF33)")
    void deveRecusarFormatoNaoAceito() throws Exception {
        enviar(EMAIL_ALUNO, "programa.exe", "MZ".getBytes(StandardCharsets.US_ASCII))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("PDF, DOCX, XLSX, PPTX, JPG, JPEG ou PNG")));
    }

    @Test
    @DisplayName("Arquivo renomeado para .pdf sem ser PDF é recusado")
    void deveRecusarConteudoQueNaoCorrespondeAExtensao() throws Exception {
        enviar(EMAIL_ALUNO, "falso.pdf", "MZ isto não é um PDF".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("não corresponde a um PDF")));
    }

    @Test
    @DisplayName("Com a entrega aguardando análise, o aluno não envia outra versão (CA03 do RF33)")
    void naoDeveAceitarNovaVersaoComEntregaEmAnalise() throws Exception {
        enviar(EMAIL_ALUNO, "relatorio.pdf", PDF).andExpect(status().isCreated());

        enviar(EMAIL_ALUNO, "relatorio-v2.pdf", PDF)
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Tarefa da trilha de outro tipo de estágio responde 404")
    void naoDeveEnviarParaTarefaDeOutraTrilha() throws Exception {
        mockMvc.perform(multipart("/api/v1/aluno/tarefas/{id}/entregas", tarefaDeOutraTrilha.getId())
                        .file(arquivo("plano.pdf", PDF))
                        .with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A trilha mostra o status da entrega de cada tarefa (RF32)")
    void trilhaDeveMostrarOStatusDaEntrega() throws Exception {
        mockMvc.perform(get("/api/v1/aluno/trilha").with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tarefas[0].statusEntrega").value("PENDENTE"));

        enviar(EMAIL_ALUNO, "relatorio.pdf", PDF).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/aluno/trilha").with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(jsonPath("$.tarefas[0].statusEntrega").value("ENVIADA"))
                .andExpect(jsonPath("$.tarefas[0].totalVersoes").value(1));
    }

    // RF35 e RF36

    @Test
    @DisplayName("O painel mostra só as entregas dos alunos do orientador (RF35, RN17)")
    void painelDeveMostrarSoAsEntregasDoOrientador() throws Exception {
        Long entregaId = idDaEntrega(enviar(EMAIL_ALUNO, "relatorio.pdf", PDF));

        mockMvc.perform(get("/api/v1/orientador/entregas").with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAguardando").value(1))
                .andExpect(jsonPath("$.itens[0].nomeTarefa").value("Relatório Parcial"))
                .andExpect(jsonPath("$.itens[0].status").value("ENVIADA"));

        mockMvc.perform(get("/api/v1/orientador/entregas").with(user(EMAIL_OUTRO_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(jsonPath("$.totalAguardando").value(0));

        mockMvc.perform(get("/api/v1/orientador/entregas/{id}", entregaId)
                        .with(user(EMAIL_OUTRO_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Abrir a entrega passa para EM_REVISAO (RF36)")
    void abrirDevePassarParaEmRevisao() throws Exception {
        Long entregaId = idDaEntrega(enviar(EMAIL_ALUNO, "relatorio.pdf", PDF));

        abrir(entregaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_REVISAO"))
                .andExpect(jsonPath("$.versoes", hasSize(1)));
    }

    @Test
    @DisplayName("Devolver sem feedback é recusado (CA01 do RF36)")
    void naoDeveDevolverSemFeedback() throws Exception {
        Long entregaId = idDaEntrega(enviar(EMAIL_ALUNO, "relatorio.pdf", PDF));

        avaliar(entregaId, "DEVOLVER", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("feedback")));
    }

    @Test
    @DisplayName("Devolução grava o feedback e avisa o aluno; a versão 2 mantém a versão 1 intacta (CA01 do RF33)")
    void deveDevolverEAceitarNovaVersao() throws Exception {
        Long entregaId = idDaEntrega(enviar(EMAIL_ALUNO, "relatorio.pdf", PDF));

        avaliar(entregaId, "DEVOLVER", "Falta a assinatura do supervisor.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEVOLVIDA"))
                .andExpect(jsonPath("$.podeEnviar").value(true))
                .andExpect(jsonPath("$.versoes[0].resultado").value("DEVOLVIDA"));
        verify(emailService).enviarResultadoEntrega(eq(EMAIL_ALUNO), anyString(), eq("Relatório Parcial"),
                eq(false), anyString());

        enviar(EMAIL_ALUNO, "relatorio-v2.pdf", PDF)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ENVIADA"))
                .andExpect(jsonPath("$.versoes", hasSize(2)))
                .andExpect(jsonPath("$.versoes[0].nomeArquivo").value("relatorio.pdf"))
                .andExpect(jsonPath("$.versoes[0].feedback").value("Falta a assinatura do supervisor."))
                .andExpect(jsonPath("$.versoes[1].numero").value(2));

        // CA02 do RF36: ao abrir a versão 2, o orientador vê a versão 1 com o feedback dado
        abrir(entregaId)
                .andExpect(jsonPath("$.versoes", hasSize(2)))
                .andExpect(jsonPath("$.versoes[0].feedback").value("Falta a assinatura do supervisor."));
    }

    @Test
    @DisplayName("Entrega aprovada não aceita nova versão nem nova avaliação")
    void deveAprovarEBloquearNovosEnvios() throws Exception {
        Long entregaId = idDaEntrega(enviar(EMAIL_ALUNO, "relatorio.pdf", PDF));

        avaliar(entregaId, "APROVAR", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADA"))
                .andExpect(jsonPath("$.versoes[0].resultado").value("APROVADA"));
        verify(emailService).enviarResultadoEntrega(eq(EMAIL_ALUNO), anyString(), eq("Relatório Parcial"),
                eq(true), anyString());

        enviar(EMAIL_ALUNO, "relatorio-v2.pdf", PDF).andExpect(status().isConflict());
        avaliar(entregaId, "APROVAR", null).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Arquivo só é baixado pelo próprio aluno e pelo orientador dele (RN17)")
    void downloadDeveRespeitarOsDonos() throws Exception {
        String resposta = enviar(EMAIL_ALUNO, "relatorio.pdf", PDF).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Long versaoId = ((Number) JsonPath.read(resposta, "$.versoes[0].id")).longValue();

        mockMvc.perform(get("/api/v1/aluno/entregas/versoes/{id}/arquivo", versaoId)
                        .with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().bytes(PDF));
        mockMvc.perform(get("/api/v1/aluno/entregas/versoes/{id}/arquivo", versaoId)
                        .with(user(EMAIL_OUTRO_ALUNO).roles("ALUNO")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/orientador/entregas/versoes/{id}/arquivo", versaoId)
                        .with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/orientador/entregas/versoes/{id}/arquivo", versaoId)
                        .with(user(EMAIL_OUTRO_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tarefa com entregas não pode ser removida da trilha")
    void naoDeveRemoverTarefaComEntregas() throws Exception {
        enviar(EMAIL_ALUNO, "relatorio.pdf", PDF).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/coordenador/trilhas/tarefas/{id}", relatorio.getId())
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR")))
                .andExpect(status().isConflict());
    }

    // auxiliares

    private ResultActions enviar(String emailAluno, String nomeArquivo, byte[] conteudo) throws Exception {
        return mockMvc.perform(multipart("/api/v1/aluno/tarefas/{id}/entregas", relatorio.getId())
                .file(arquivo(nomeArquivo, conteudo))
                .param("comentario", "Primeira versão")
                .with(user(emailAluno).roles("ALUNO")));
    }

    private ResultActions abrir(Long entregaId) throws Exception {
        return mockMvc.perform(get("/api/v1/orientador/entregas/{id}", entregaId)
                .with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR")));
    }

    private ResultActions avaliar(Long entregaId, String decisao, String feedback) throws Exception {
        String corpo = """
                {"decisao": "%s", "feedback": %s}
                """.formatted(decisao, feedback == null ? "null" : "\"" + feedback + "\"");
        return mockMvc.perform(post("/api/v1/orientador/entregas/{id}/avaliacao", entregaId)
                .with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR"))
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private Long idDaEntrega(ResultActions envio) throws Exception {
        String resposta = envio.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(resposta, "$.entregaId")).longValue();
    }

    private MockMultipartFile arquivo(String nome, byte[] conteudo) {
        return new MockMultipartFile("arquivo", nome, "application/octet-stream", conteudo);
    }

    private Trilha criarTrilha(TipoEstagio tipo) {
        Trilha trilha = new Trilha();
        trilha.setTipoEstagio(tipo);
        trilha.setNome("Trilha " + tipo.getNome());
        entityManager.persist(trilha);
        return trilha;
    }

    private Tarefa criarTarefa(Trilha trilha, String nome) {
        Tarefa tarefa = new Tarefa();
        tarefa.setTrilha(trilha);
        tarefa.setNome(nome);
        tarefa.setOrdem(1);
        entityManager.persist(tarefa);
        return tarefa;
    }
}
