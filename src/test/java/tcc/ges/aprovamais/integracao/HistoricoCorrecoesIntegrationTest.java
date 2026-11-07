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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Histórico de correções do orientador com o banco H2 do perfil de testes
class HistoricoCorrecoesIntegrationTest extends CoordenacaoTestBase {

    private static final String URL = "/api/v1/orientador/entregas/historico";
    private static final String EMAIL_NOVO_ORIENTADOR = "novo.orientador@teste.com";

    private Orientador orientador;
    private Orientador novoOrientador;
    private Estagio estagio;
    private Trilha trilha;

    @BeforeEach
    void preparar() {
        Turma turma = criarTurma();
        TipoEstagio tipo = criarTipoEstagio(turma.getCurso());
        orientador = criarOrientador(EMAIL_ORIENTADOR, "ORI-001");
        novoOrientador = criarOrientador(EMAIL_NOVO_ORIENTADOR, "ORI-002");
        vincularOrientadorNaTurma(orientador, turma);
        estagio = criarEstagio(criarMatricula(criarAluno(EMAIL_ALUNO, "RGM-001"), turma), orientador, tipo, StatusEstagio.ATIVO);

        trilha = new Trilha();
        trilha.setTipoEstagio(tipo);
        trilha.setNome("Trilha Estagiario");
        entityManager.persist(trilha);
    }

    @Test
    @DisplayName("25 correções em páginas de 10 formam 3 páginas, a última com 5 (CA01 do RF38)")
    void deveDividirEmPaginas() throws Exception {
        // Uma entrega com 25 versões avaliadas: 24 devolvidas e a última aprovada
        Entrega entrega = criarEntrega(criarTarefa("Relatório Parcial", 1), StatusEntrega.APROVADA);
        for (int numero = 1; numero <= 25; numero++) {
            criarVersao(entrega, numero, numero == 25 ? StatusEntrega.APROVADA : StatusEntrega.DEVOLVIDA,
                    orientador, OffsetDateTime.now().minusDays(30 - numero));
        }
        entityManager.flush();

        mockMvc.perform(get(URL).param("pagina", "0").param("tamanho", "10")
                        .with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCorrecoes").value(25))
                .andExpect(jsonPath("$.totalPaginas").value(3))
                .andExpect(jsonPath("$.itens", hasSize(10)))
                // Da mais recente para a mais antiga
                .andExpect(jsonPath("$.itens[0].numeroVersao").value(25))
                .andExpect(jsonPath("$.itens[0].resultado").value("APROVADA"))
                .andExpect(jsonPath("$.itens[1].numeroVersao").value(24))
                .andExpect(jsonPath("$.itens[1].resultado").value("DEVOLVIDA"));

        mockMvc.perform(get(URL).param("pagina", "2").param("tamanho", "10")
                        .with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(jsonPath("$.pagina").value(2))
                .andExpect(jsonPath("$.itens", hasSize(5)))
                .andExpect(jsonPath("$.itens[4].numeroVersao").value(1));
    }

    @Test
    @DisplayName("Depois da troca de orientador, cada um vê só as correções que ele fez (CA02 do RF38)")
    void cadaOrientadorDeveVerSoAsProprias() throws Exception {
        Entrega entrega = criarEntrega(criarTarefa("Termo de Compromisso", 1), StatusEntrega.DEVOLVIDA);
        criarVersao(entrega, 1, StatusEntrega.DEVOLVIDA, orientador, OffsetDateTime.now().minusDays(2));

        // Troca de turma: o estágio passa para o novo orientador, que devolve a versão 2
        estagio.setOrientador(novoOrientador);
        criarVersao(entrega, 2, StatusEntrega.DEVOLVIDA, novoOrientador, OffsetDateTime.now().minusDays(1));
        entityManager.flush();

        mockMvc.perform(get(URL).with(user(EMAIL_NOVO_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCorrecoes").value(1))
                .andExpect(jsonPath("$.itens[0].numeroVersao").value(2));

        mockMvc.perform(get(URL).with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(jsonPath("$.totalCorrecoes").value(1))
                .andExpect(jsonPath("$.itens[0].numeroVersao").value(1))
                .andExpect(jsonPath("$.itens[0].nomeAluno").value("Aluno Teste"))
                .andExpect(jsonPath("$.itens[0].nomeTarefa").value("Termo de Compromisso"))
                .andExpect(jsonPath("$.itens[0].feedback").value("Feedback da versão 1"));
    }

    @Test
    @DisplayName("Versão enviada e ainda não avaliada não entra no histórico")
    void versaoSemAvaliacaoNaoDeveAparecer() throws Exception {
        Entrega entrega = criarEntrega(criarTarefa("Relatório Final", 1), StatusEntrega.ENVIADA);
        criarVersao(entrega, 1, null, null, null);
        entityManager.flush();

        mockMvc.perform(get(URL).with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCorrecoes").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(1))
                .andExpect(jsonPath("$.itens", hasSize(0)));
    }

    @Test
    @DisplayName("A página do histórico abre pelo link da barra do orientador")
    void paginaDeveAbrirPelaBarra() throws Exception {
        mockMvc.perform(get("/orientador/historico").with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/orientador/historico\"")))
                .andExpect(content().string(containsString("/js/paginas/orientador-historico.js")));
    }

    @Test
    @DisplayName("Outro perfil não acessa a API nem a página do histórico")
    void outroPerfilDeveSerBloqueado() throws Exception {
        mockMvc.perform(get(URL).with(user(EMAIL_ALUNO).roles("ALUNO"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/orientador/historico").with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(status().isForbidden());
    }

    // auxiliares

    private Tarefa criarTarefa(String nome, int ordem) {
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

    private void criarVersao(Entrega entrega, int numero, StatusEntrega resultado, Orientador avaliador,
                             OffsetDateTime avaliadaEm) {
        EntregaVersao versao = new EntregaVersao();
        versao.setEntrega(entrega);
        versao.setNumero(numero);
        versao.setNomeArquivo("arquivo-v" + numero + ".pdf");
        versao.setChaveArquivo("teste/" + entrega.getTarefa().getNome() + "-v" + numero + ".pdf");
        versao.setTipoArquivo(TipoArquivo.PDF);
        versao.setTamanhoBytes(1024L);
        versao.setResultado(resultado);
        versao.setFeedback(resultado != null ? "Feedback da versão " + numero : null);
        versao.setAvaliador(avaliador);
        versao.setAvaliadaEm(avaliadaEm);
        entityManager.persist(versao);
    }
}
