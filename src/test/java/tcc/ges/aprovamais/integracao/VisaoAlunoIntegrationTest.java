package tcc.ges.aprovamais.integracao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.StatusEntrega;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.entity.enums.TipoArquivo;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Visão consolidada do aluno para o orientador (RF37), com o banco H2 do perfil de testes
class VisaoAlunoIntegrationTest extends CoordenacaoTestBase {

    private static final String EMAIL_OUTRO_ORIENTADOR = "outro.orientador@teste.com";

    private Estagio estagio;
    private Orientador orientador;

    /*
       Trilha com 3 tarefas: a 1ª foi devolvida e reenviada (2 versões), a 2ª foi aprovada
       sem feedback e a 3ª ainda não foi enviada
    */
    @BeforeEach
    void preparar() {
        Turma turma = criarTurma();
        TipoEstagio tipo = criarTipoEstagio(turma.getCurso());
        orientador = criarOrientador(EMAIL_ORIENTADOR, "ORI-001");
        criarOrientador(EMAIL_OUTRO_ORIENTADOR, "ORI-002");
        vincularOrientadorNaTurma(orientador, turma);
        estagio = criarEstagio(criarMatricula(criarAluno(EMAIL_ALUNO, "RGM-001"), turma), orientador, tipo, StatusEstagio.ATIVO);

        Trilha trilha = new Trilha();
        trilha.setTipoEstagio(tipo);
        trilha.setNome("Trilha Estagiario");
        entityManager.persist(trilha);
        Tarefa termo = criarTarefa(trilha, "Termo de Compromisso", 1);
        Tarefa parcial = criarTarefa(trilha, "Relatório Parcial", 2);
        criarTarefa(trilha, "Relatório Final", 3);

        Entrega entregaTermo = criarEntrega(termo, StatusEntrega.ENVIADA);
        criarVersao(entregaTermo, 1, StatusEntrega.DEVOLVIDA, "Falta a assinatura do supervisor.");
        criarVersao(entregaTermo, 2, null, null);

        Entrega entregaParcial = criarEntrega(parcial, StatusEntrega.APROVADA);
        criarVersao(entregaParcial, 1, StatusEntrega.APROVADA, null);
        entityManager.flush();
    }

    @Test
    @DisplayName("Mostra as tarefas na ordem, com status, número de versões e último feedback (CA01 do RF37)")
    void deveMostrarAsTarefasComVersoesEFeedback() throws Exception {
        mockMvc.perform(get("/api/v1/orientador/estagios/{id}/visao", estagio.getId())
                        .with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tarefas", hasSize(3)))
                .andExpect(jsonPath("$.tarefas[0].nomeTarefa").value("Termo de Compromisso"))
                .andExpect(jsonPath("$.tarefas[0].status").value("ENVIADA"))
                .andExpect(jsonPath("$.tarefas[0].numeroVersoes").value(2))
                .andExpect(jsonPath("$.tarefas[0].ultimoFeedback").value("Falta a assinatura do supervisor."))
                .andExpect(jsonPath("$.tarefas[0].versaoDoUltimoFeedback").value(1))
                .andExpect(jsonPath("$.tarefas[1].status").value("APROVADA"))
                .andExpect(jsonPath("$.tarefas[1].numeroVersoes").value(1))
                .andExpect(jsonPath("$.tarefas[1].ultimoFeedback").value(nullValue()))
                .andExpect(jsonPath("$.tarefas[2].status").value("PENDENTE"))
                .andExpect(jsonPath("$.tarefas[2].numeroVersoes").value(0))
                .andExpect(jsonPath("$.tarefas[2].entregaId").value(nullValue()));
    }

    @Test
    @DisplayName("Mostra o progresso com as mesmas contas do painel (passo 3 do RF37)")
    void deveMostrarOProgresso() throws Exception {
        mockMvc.perform(get("/api/v1/orientador/estagios/{id}/visao", estagio.getId())
                        .with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progresso.nomeAluno").value("Aluno Teste"))
                .andExpect(jsonPath("$.progresso.atividadesTotal").value(3))
                .andExpect(jsonPath("$.progresso.atividadesAprovadas").value(1))
                .andExpect(jsonPath("$.progresso.atividadesRestantes").value(2))
                .andExpect(jsonPath("$.progresso.cargaHorariaNecessaria").value(270));
    }

    @Test
    @DisplayName("Aluno de outro orientador responde 404 (CA02 do RF37)")
    void alunoDeOutroOrientadorDeveResponder404() throws Exception {
        mockMvc.perform(get("/api/v1/orientador/estagios/{id}/visao", estagio.getId())
                        .with(user(EMAIL_OUTRO_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Estágio que não está ATIVO responde 404")
    void estagioNaoAtivoDeveResponder404() throws Exception {
        estagio.setStatus(StatusEstagio.PENDENTE);
        entityManager.flush();

        mockMvc.perform(get("/api/v1/orientador/estagios/{id}/visao", estagio.getId())
                        .with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A tela de validações tem o botão do acompanhamento, e a página abre para o orientador")
    void paginaDeveAbrirPeloBotaoDosEstagiosAtivos() throws Exception {
        mockMvc.perform(get("/orientador/validacoes").with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/orientador/acompanhamento/" + estagio.getId())));

        mockMvc.perform(get("/orientador/acompanhamento/{id}", estagio.getId())
                        .with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/js/paginas/orientador-acompanhamento.js")));
    }

    @Test
    @DisplayName("Outro perfil não acessa a API nem a página do acompanhamento")
    void outroPerfilDeveSerBloqueado() throws Exception {
        mockMvc.perform(get("/api/v1/orientador/estagios/{id}/visao", estagio.getId())
                        .with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/orientador/acompanhamento/{id}", estagio.getId())
                        .with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(status().isForbidden());
    }

    // auxiliares

    private Tarefa criarTarefa(Trilha trilha, String nome, int ordem) {
        Tarefa tarefa = new Tarefa();
        tarefa.setTrilha(trilha);
        tarefa.setNome(nome);
        tarefa.setOrdem(ordem);
        entityManager.persist(tarefa);
        return tarefa;
    }

    private Entrega criarEntrega(Tarefa tarefa, StatusEntrega status) {
        Entrega entrega = new Entrega();
        entrega.setEstagio(estagio);
        entrega.setTarefa(tarefa);
        entrega.setStatus(status);
        entityManager.persist(entrega);
        return entrega;
    }

    private void criarVersao(Entrega entrega, int numero, StatusEntrega resultado, String feedback) {
        EntregaVersao versao = new EntregaVersao();
        versao.setEntrega(entrega);
        versao.setNumero(numero);
        versao.setNomeArquivo("arquivo-v" + numero + ".pdf");
        versao.setChaveArquivo("teste/arquivo-" + entrega.getTarefa().getNome() + "-v" + numero + ".pdf");
        versao.setTipoArquivo(TipoArquivo.PDF);
        versao.setTamanhoBytes(1024L);
        if (resultado != null) {
            versao.setResultado(resultado);
            versao.setFeedback(feedback);
            versao.setAvaliador(orientador);
            versao.setAvaliadaEm(OffsetDateTime.now().minusDays(3 - numero));
        }
        entityManager.persist(versao);
    }
}
