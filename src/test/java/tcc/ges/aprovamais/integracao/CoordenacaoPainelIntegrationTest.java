package tcc.ges.aprovamais.integracao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.Modalidade;
import tcc.ges.aprovamais.entity.enums.Periodo;
import tcc.ges.aprovamais.entity.enums.StatusEntrega;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
   Dashboard do coordenador com o banco de verdade (H2). Aqui conferimos que as consultas do
   PainelCoordenadorRepository funcionam, que um coordenador só vê os próprios cursos (RN21)
   e a segurança da rota (403 para outro perfil e 404 para curso de outro coordenador).

   Cenário montado antes de cada teste:
   - Engenharia (do coordenador), turma 8B, encerramento das entregas daqui a 15 dias:
     1 estágio ATIVO com 1 de 2 tarefas aprovada e 1 enviada, e 1 estágio PENDENTE com orientador;
   - Sistemas (do coordenador), turma 6A, sem data de encerramento:
     1 estágio PENDENTE sem orientador e 1 estágio ATIVO com uma entrega DEVOLVIDA;
   - curso do outro coordenador, turma 9C: 1 estágio ATIVO com entrega EM_REVISAO e 1 PENDENTE sem orientador.
     Nada dele pode aparecer nos números do coordenador
*/
class CoordenacaoPainelIntegrationTest extends CoordenacaoTestBase {

    private static final String URL = "/api/v1/coordenador/painel";

    private Curso engenharia;
    private Curso sistemas;
    private Curso cursoDoOutro;
    private Orientador orientador;
    private int proximoAluno = 1;

    @BeforeEach
    void montarCenario() {
        orientador = criarOrientador(EMAIL_ORIENTADOR, "ORI-PAINEL-1");

        engenharia = criarCurso("Engenharia de Software", "MEC-PAINEL-1", coordenador);
        sistemas = criarCurso("Sistemas de Informação", "MEC-PAINEL-2", coordenador);
        cursoDoOutro = criarCurso("Ciência da Computação", "MEC-PAINEL-3", outroCoordenador);

        // Engenharia, turma 8B
        Turma t8b = turma(engenharia, "8B", LocalDate.now().plusDays(15));
        TipoEstagio tipoEngenharia = criarTipoEstagio(engenharia);
        List<Tarefa> tarefasEngenharia = criarTrilha(tipoEngenharia, 2);
        Estagio ativo8b = estagio(t8b, orientador, tipoEngenharia, StatusEstagio.ATIVO);
        entrega(ativo8b, tarefasEngenharia.get(0), StatusEntrega.APROVADA);
        entrega(ativo8b, tarefasEngenharia.get(1), StatusEntrega.ENVIADA);
        estagio(t8b, orientador, tipoEngenharia, StatusEstagio.PENDENTE);

        // Sistemas, turma 6A, sem data de encerramento
        Turma t6a = turma(sistemas, "6A", null);
        TipoEstagio tipoSistemas = criarTipoEstagio(sistemas);
        List<Tarefa> tarefasSistemas = criarTrilha(tipoSistemas, 1);
        estagio(t6a, null, tipoSistemas, StatusEstagio.PENDENTE);
        Estagio ativo6a = estagio(t6a, orientador, tipoSistemas, StatusEstagio.ATIVO);
        entrega(ativo6a, tarefasSistemas.get(0), StatusEntrega.DEVOLVIDA);

        // Curso do outro coordenador, turma 9C
        Turma t9c = turma(cursoDoOutro, "9C", LocalDate.now().plusDays(5));
        TipoEstagio tipoOutro = criarTipoEstagio(cursoDoOutro);
        List<Tarefa> tarefasOutro = criarTrilha(tipoOutro, 1);
        Estagio ativoOutro = estagio(t9c, orientador, tipoOutro, StatusEstagio.ATIVO);
        entrega(ativoOutro, tarefasOutro.get(0), StatusEntrega.EM_REVISAO);
        estagio(t9c, null, tipoOutro, StatusEstagio.PENDENTE);

        entityManager.flush();
    }

    // ---------- Ajudantes do cenário ----------

    private Turma turma(Curso curso, String identificador, LocalDate dataEncerramentoEntregas) {
        Turma turma = new Turma();
        turma.setCurso(curso);
        turma.setSemestre("2026/2");
        turma.setIdentificador(identificador);
        turma.setPeriodo(Periodo.NOTURNO);
        turma.setModalidade(Modalidade.PRESENCIAL);
        turma.setDataEncerramentoEntregas(dataEncerramentoEntregas);
        entityManager.persist(turma);
        return turma;
    }

    private List<Tarefa> criarTrilha(TipoEstagio tipo, int quantidade) {
        Trilha trilha = new Trilha();
        trilha.setTipoEstagio(tipo);
        trilha.setNome("Trilha " + tipo.getNome());
        entityManager.persist(trilha);
        List<Tarefa> tarefas = new ArrayList<>();
        for (int ordem = 1; ordem <= quantidade; ordem++) {
            Tarefa tarefa = new Tarefa();
            tarefa.setTrilha(trilha);
            tarefa.setNome("Tarefa " + ordem);
            tarefa.setOrdem(ordem);
            entityManager.persist(tarefa);
            tarefas.add(tarefa);
        }
        return tarefas;
    }

    // Cada estágio tem um aluno próprio, com e-mail e RGM diferentes
    private Estagio estagio(Turma turma, Orientador orientadorDoEstagio, TipoEstagio tipo, StatusEstagio status) {
        Aluno aluno = criarAluno("aluno.painel" + proximoAluno + "@teste.com", "RGM-PAINEL-" + proximoAluno);
        proximoAluno++;
        Matricula matricula = criarMatricula(aluno, turma);
        return criarEstagio(matricula, orientadorDoEstagio, tipo, status);
    }

    private void entrega(Estagio estagio, Tarefa tarefa, StatusEntrega status) {
        Entrega entrega = new Entrega();
        entrega.setEstagio(estagio);
        entrega.setTarefa(tarefa);
        entrega.setStatus(status);
        entityManager.persist(entrega);
    }

    // ---------- Testes ----------

    @Test
    @DisplayName("CA01, CA02 e CA04: todos os cursos do coordenador, sem nada do curso do outro coordenador")
    void deveMontarOPainelComTodosOsCursosDoCoordenador() throws Exception {
        mockMvc.perform(get(URL).with(user(EMAIL_COORDENADOR).roles("COORDENADOR")))
                .andExpect(status().isOk())
                // Cartões 1, 2 e 3: o PENDENTE sem orientador e o ATIVO do outro curso não entram
                .andExpect(jsonPath("$.estagiosSemOrientador").value(1))
                .andExpect(jsonPath("$.estagiosAguardandoValidacao").value(1))
                .andExpect(jsonPath("$.estagiosEmAndamento").value(2))
                // Cartão 4: só a 6A (ATIVO e sem data)
                .andExpect(jsonPath("$.turmasSemDataEncerramento").value(1))
                // Cartão 5: só a ENVIADA; APROVADA, DEVOLVIDA e a EM_REVISAO do outro curso não entram
                .andExpect(jsonPath("$.tarefasAguardandoCorrecao").value(1))
                // Cartão 6: a 8B tem 1 de 2 tarefas aprovada e faltam 15 dias
                .andExpect(jsonPath("$.estagiosRetaFinal").value(1))
                .andExpect(jsonPath("$.retaFinalSoTarefas").value(1))
                .andExpect(jsonPath("$.retaFinalSoHoras").value(0))
                .andExpect(jsonPath("$.retaFinalDuasPendencias").value(0))
                // Gráficos
                .andExpect(jsonPath("$.situacaoPorTurma.length()").value(2))
                .andExpect(jsonPath("$.correcaoPorTurma.length()").value(1))
                .andExpect(jsonPath("$.correcaoPorTurma[0].turma").value("8B"))
                .andExpect(jsonPath("$.correcaoPorTurma[0].orientador").value("Orientador Teste"))
                .andExpect(jsonPath("$.prazoPorTurma.length()").value(1))
                .andExpect(jsonPath("$.prazoPorTurma[0].faixa").value("AMARELO"))
                .andExpect(jsonPath("$.prazoPorTurma[0].textoPrazo").value("faltam 15 dias"));
    }

    @Test
    @DisplayName("CA02: o outro coordenador vê só o curso dele")
    void outroCoordenadorDeveVerSoOProprioCurso() throws Exception {
        mockMvc.perform(get(URL).with(user(EMAIL_OUTRO_COORDENADOR).roles("COORDENADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estagiosSemOrientador").value(1))
                .andExpect(jsonPath("$.estagiosAguardandoValidacao").value(0))
                .andExpect(jsonPath("$.estagiosEmAndamento").value(1))
                .andExpect(jsonPath("$.tarefasAguardandoCorrecao").value(1))
                .andExpect(jsonPath("$.situacaoPorTurma.length()").value(1))
                .andExpect(jsonPath("$.situacaoPorTurma[0].turma").value("9C"));
    }

    @Test
    @DisplayName("CA11: coordenador sem cursos recebe tudo 0, sem erro")
    void coordenadorSemCursosDeveReceberTudoZero() throws Exception {
        criarCoordenador("novo.coordenador@teste.com");
        entityManager.flush();

        mockMvc.perform(get(URL).with(user("novo.coordenador@teste.com").roles("COORDENADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estagiosSemOrientador").value(0))
                .andExpect(jsonPath("$.estagiosEmAndamento").value(0))
                .andExpect(jsonPath("$.tarefasAguardandoCorrecao").value(0))
                .andExpect(jsonPath("$.estagiosRetaFinal").value(0))
                .andExpect(jsonPath("$.situacaoPorTurma.length()").value(0));
    }

    @Test
    @DisplayName("CA12: orientador chamando a API do painel recebe 403")
    void orientadorNaoDeveAcessarOPainel() throws Exception {
        mockMvc.perform(get(URL).with(user(EMAIL_ORIENTADOR).roles("ORIENTADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CA13: curso de outro coordenador ou inexistente responde 404")
    void cursoDeOutroCoordenadorDeveResponder404() throws Exception {
        mockMvc.perform(get(URL).param("cursoId", cursoDoOutro.getId().toString())
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(URL).param("cursoId", "999999")
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CA14: com o curso Sistemas escolhido, os números são só da turma 6A")
    void deveFiltrarPeloCursoEscolhido() throws Exception {
        mockMvc.perform(get(URL).param("cursoId", sistemas.getId().toString())
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estagiosSemOrientador").value(1))
                .andExpect(jsonPath("$.estagiosAguardandoValidacao").value(0))
                .andExpect(jsonPath("$.estagiosEmAndamento").value(1))
                .andExpect(jsonPath("$.turmasSemDataEncerramento").value(1))
                .andExpect(jsonPath("$.tarefasAguardandoCorrecao").value(0))
                .andExpect(jsonPath("$.estagiosRetaFinal").value(0))
                .andExpect(jsonPath("$.situacaoPorTurma.length()").value(1))
                .andExpect(jsonPath("$.situacaoPorTurma[0].turma").value("6A"));
    }

    @Test
    @DisplayName("A página do painel abre com a jornada, os gráficos e os 4 atalhos de cadastro")
    void paginaDoPainelDeveAbrirComOsAtalhos() throws Exception {
        mockMvc.perform(get("/coordenador/dashboard").with(user(EMAIL_COORDENADOR).roles("COORDENADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Jornada do estágio")))
                .andExpect(content().string(containsString("Prazo das entregas por turma")))
                .andExpect(content().string(containsString("/js/paginas/coordenador-dashboard.js")))
                .andExpect(content().string(containsString("/coordenador/cursos")))
                .andExpect(content().string(containsString("/coordenador/turmas")))
                .andExpect(content().string(containsString("/coordenador/tipos-estagio")))
                .andExpect(content().string(containsString("/coordenador/trilhas")));
    }
}
