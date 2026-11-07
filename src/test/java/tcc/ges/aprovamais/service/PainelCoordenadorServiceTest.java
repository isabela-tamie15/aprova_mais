package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tcc.ges.aprovamais.dto.PainelCoordenadorResponse;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.StatusEntrega;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.PainelCoordenadorRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PainelCoordenadorServiceTest {

    // Dashboard do coordenador: o banco é simulado com o Mockito, então aqui testamos só as contas do service

    private static final String EMAIL = "coordenador@teste.com";
    private static final Long TIPO_ESTAGIARIO = 7L;

    @Mock
    private PainelCoordenadorRepository painelCoordenadorRepository;
    @Mock
    private CursoService cursoService;
    @InjectMocks
    private PainelCoordenadorService painelCoordenadorService;

    private final LocalDate hoje = LocalDate.now();
    private Curso engenharia;
    private Curso sistemas;
    private Orientador carlos;
    private Orientador luisa;
    private long proximoId = 1;

    @BeforeEach
    void setUp() {
        engenharia = curso(1L, "Eng. de Software");
        sistemas = curso(2L, "Sistemas de Informação");
        carlos = orientador(50L, "Prof. Carlos");
        luisa = orientador(51L, "Profa. Luísa");
    }

    // ---------- Ajudantes para montar os dados do teste ----------

    private Curso curso(Long id, String nome) {
        Curso curso = new Curso();
        curso.setId(id);
        curso.setNome(nome);
        return curso;
    }

    private Orientador orientador(Long id, String nome) {
        Orientador orientador = new Orientador();
        orientador.setId(id);
        orientador.setNome(nome);
        return orientador;
    }

    // Turma do semestre 2026/2. A data de encerramento pode ser nula (é o caso do cartão 4)
    private Turma turma(Long id, String identificador, Curso curso, LocalDate dataEncerramentoEntregas) {
        Turma turma = new Turma();
        turma.setId(id);
        turma.setIdentificador(identificador);
        turma.setSemestre("2026/2");
        turma.setCurso(curso);
        turma.setDataEncerramentoEntregas(dataEncerramentoEntregas);
        return turma;
    }

    /*
       Estágio do tipo Estagiário. Por padrão, a data prevista de conclusão fica bem antes de hoje,
       ou seja, as horas fecham no prazo; os testes de horas trocam essa data
    */
    private Estagio estagio(Turma turma, StatusEstagio status, Orientador orientador) {
        Matricula matricula = new Matricula();
        matricula.setTurma(turma);
        TipoEstagio tipo = new TipoEstagio();
        tipo.setId(TIPO_ESTAGIARIO);

        Estagio estagio = new Estagio();
        estagio.setId(proximoId++);
        estagio.setMatricula(matricula);
        estagio.setTipoEstagio(tipo);
        estagio.setStatus(status);
        estagio.setOrientador(orientador);
        estagio.setDataPrevistaConclusao(hoje.minusDays(60));
        return estagio;
    }

    private Entrega entrega(Estagio estagio) {
        Entrega entrega = new Entrega();
        entrega.setEstagio(estagio);
        entrega.setStatus(StatusEntrega.ENVIADA);
        return entrega;
    }

    // Lista com o mesmo id repetido. Exemplo: repetir(7, 3) = [7, 7, 7], ou seja, 3 tarefas do tipo 7
    private List<Long> repetir(Long id, int vezes) {
        return new ArrayList<>(Collections.nCopies(vezes, id));
    }

    // Simula as 4 consultas do PainelCoordenadorRepository
    private void prepararBanco(List<Estagio> estagios, List<Long> tipoDasTarefas,
                               List<Long> estagioDasAprovadas, List<Entrega> entregasAguardando) {
        when(painelCoordenadorRepository.listarEstagiosDosCursos(eq(EMAIL), any())).thenReturn(estagios);
        when(painelCoordenadorRepository.listarTipoEstagioDasTarefas(EMAIL)).thenReturn(tipoDasTarefas);
        when(painelCoordenadorRepository.listarEstagioDasEntregas(EMAIL, StatusEstagio.ATIVO, StatusEntrega.APROVADA))
                .thenReturn(estagioDasAprovadas);
        when(painelCoordenadorRepository.listarEntregasDosCursos(EMAIL, CorrecaoService.AGUARDANDO_ANALISE))
                .thenReturn(entregasAguardando);
    }

    // ---------- CA01: cartões 1, 2 e 3 ----------

    @Test
    @DisplayName("CA01: cartões 1, 2 e 3 contam os estágios sem orientador, aguardando validação e em andamento")
    void deveContarOsCartoesUmDoisETres() {
        // arrange: na 6A, 2 sem orientador e 1 em andamento; na 8A, 2 aguardando validação e 3 em andamento
        Turma t6a = turma(3L, "6A", sistemas, hoje.plusDays(90));
        Turma t8a = turma(2L, "8A", engenharia, hoje.plusDays(90));
        List<Estagio> estagios = List.of(
                estagio(t6a, StatusEstagio.PENDENTE, null),
                estagio(t6a, StatusEstagio.PENDENTE, null),
                estagio(t6a, StatusEstagio.ATIVO, carlos),
                estagio(t8a, StatusEstagio.PENDENTE, luisa),
                estagio(t8a, StatusEstagio.PENDENTE, luisa),
                estagio(t8a, StatusEstagio.ATIVO, luisa),
                estagio(t8a, StatusEstagio.ATIVO, luisa),
                estagio(t8a, StatusEstagio.ATIVO, luisa));
        prepararBanco(estagios, List.of(), List.of(), List.of());

        // act
        PainelCoordenadorResponse painel = painelCoordenadorService.montarPainel(EMAIL, null);

        // assert
        assertEquals(2, painel.getEstagiosSemOrientador());
        assertEquals(2, painel.getEstagiosAguardandoValidacao());
        assertEquals(4, painel.getEstagiosEmAndamento());
    }

    @Test
    @DisplayName("CA01: gráfico 1 separa os cartões 1, 2 e 3 por turma, do maior total para o menor")
    void deveMontarOGraficoDeSituacaoPorTurma() {
        // arrange: 8A com 5 estágios e 6A com 3
        Turma t6a = turma(3L, "6A", sistemas, hoje.plusDays(90));
        Turma t8a = turma(2L, "8A", engenharia, hoje.plusDays(90));
        List<Estagio> estagios = List.of(
                estagio(t6a, StatusEstagio.PENDENTE, null),
                estagio(t6a, StatusEstagio.PENDENTE, null),
                estagio(t6a, StatusEstagio.ATIVO, carlos),
                estagio(t8a, StatusEstagio.PENDENTE, luisa),
                estagio(t8a, StatusEstagio.PENDENTE, luisa),
                estagio(t8a, StatusEstagio.ATIVO, luisa),
                estagio(t8a, StatusEstagio.ATIVO, luisa),
                estagio(t8a, StatusEstagio.ATIVO, luisa));
        prepararBanco(estagios, List.of(), List.of(), List.of());

        // act
        List<PainelCoordenadorResponse.SituacaoTurma> linhas =
                painelCoordenadorService.montarPainel(EMAIL, null).getSituacaoPorTurma();

        // assert: a 8A vem primeiro e fica com a barra cheia; a 6A tem 3 de 5 = 60
        assertEquals(2, linhas.size());
        assertEquals("8A", linhas.get(0).getTurma());
        assertEquals("2026/2", linhas.get(0).getSemestre());
        assertEquals("Eng. de Software", linhas.get(0).getCurso());
        assertEquals(5, linhas.get(0).getTotal());
        assertEquals(100.0, linhas.get(0).getPercentualBarra());
        assertEquals(40.0, linhas.get(0).getPercentualAguardandoValidacao());
        assertEquals(60.0, linhas.get(0).getPercentualEmAndamento());
        assertEquals("6A", linhas.get(1).getTurma());
        assertEquals(2, linhas.get(1).getSemOrientador());
        assertEquals(1, linhas.get(1).getEmAndamento());
        assertEquals(60.0, linhas.get(1).getPercentualBarra());
    }

    // ---------- CA03: cartão 4 ----------

    @Test
    @DisplayName("CA03: cartão 4 conta só as turmas com estágio ATIVO e sem data de encerramento das entregas")
    void deveContarTurmasAtivasSemDataDeEncerramento() {
        // arrange
        Turma semDataComAtivos = turma(1L, "8B", engenharia, null);
        Turma semDataSoPendente = turma(2L, "8A", engenharia, null);
        Turma comData = turma(3L, "6A", sistemas, hoje.plusDays(90));
        List<Estagio> estagios = List.of(
                estagio(semDataComAtivos, StatusEstagio.ATIVO, carlos),
                estagio(semDataComAtivos, StatusEstagio.ATIVO, carlos),
                estagio(semDataSoPendente, StatusEstagio.PENDENTE, luisa),
                estagio(comData, StatusEstagio.ATIVO, carlos));
        prepararBanco(estagios, List.of(), List.of(), List.of());

        // act
        PainelCoordenadorResponse painel = painelCoordenadorService.montarPainel(EMAIL, null);

        // assert: a 8B conta uma vez só, mesmo com 2 estágios; a 8A não tem ATIVO; a 6A tem data
        assertEquals(1, painel.getTurmasSemDataEncerramento());
    }

    // ---------- CA04: cartão 5 ----------

    @Test
    @DisplayName("CA04: cartão 5 conta as entregas ENVIADA ou EM_REVISAO, as mesmas do painel do orientador")
    void deveContarTarefasAguardandoCorrecao() {
        // arrange
        Turma t8b = turma(1L, "8B", engenharia, hoje.plusDays(90));
        Estagio estagio = estagio(t8b, StatusEstagio.ATIVO, carlos);
        Entrega emRevisao = entrega(estagio);
        emRevisao.setStatus(StatusEntrega.EM_REVISAO);
        prepararBanco(List.of(estagio), List.of(), List.of(), List.of(entrega(estagio), emRevisao));

        // act
        PainelCoordenadorResponse painel = painelCoordenadorService.montarPainel(EMAIL, null);

        // assert: o service pede ao banco exatamente os status do painel de correção
        assertEquals(2, painel.getTarefasAguardandoCorrecao());
        verify(painelCoordenadorRepository).listarEntregasDosCursos(EMAIL, CorrecaoService.AGUARDANDO_ANALISE);
    }

    // ---------- CA05 a CA08: cartão 6 ----------

    @Test
    @DisplayName("CA05: cartão 6, linha 'com tarefas pendentes' quando só falta aprovar tarefa")
    void deveContarSoTarefasPendentes() {
        // arrange: trilha de 3 tarefas, 1 aprovada, horas fecham no prazo, faltam 15 dias
        Turma t8b = turma(1L, "8B", engenharia, hoje.plusDays(15));
        Estagio estagio = estagio(t8b, StatusEstagio.ATIVO, carlos);
        prepararBanco(List.of(estagio), repetir(TIPO_ESTAGIARIO, 3), repetir(estagio.getId(), 1), List.of());

        // act
        PainelCoordenadorResponse painel = painelCoordenadorService.montarPainel(EMAIL, null);

        // assert
        assertEquals(1, painel.getEstagiosRetaFinal());
        assertEquals(1, painel.getRetaFinalSoTarefas());
        assertEquals(0, painel.getRetaFinalSoHoras());
        assertEquals(0, painel.getRetaFinalDuasPendencias());
    }

    @Test
    @DisplayName("CA06: cartão 6, linha 'com horas que não fecham no prazo' quando só as horas passam da data")
    void deveContarSoHorasQueNaoFecham() {
        // arrange: as 3 tarefas aprovadas, mas o aluno só completa as horas 20 dias depois do encerramento
        Turma t8a = turma(2L, "8A", engenharia, hoje.plusDays(5));
        Estagio estagio = estagio(t8a, StatusEstagio.ATIVO, luisa);
        estagio.setDataPrevistaConclusao(hoje.plusDays(25));
        prepararBanco(List.of(estagio), repetir(TIPO_ESTAGIARIO, 3), repetir(estagio.getId(), 3), List.of());

        // act
        PainelCoordenadorResponse painel = painelCoordenadorService.montarPainel(EMAIL, null);

        // assert
        assertEquals(1, painel.getEstagiosRetaFinal());
        assertEquals(0, painel.getRetaFinalSoTarefas());
        assertEquals(1, painel.getRetaFinalSoHoras());
        assertEquals(0, painel.getRetaFinalDuasPendencias());
    }

    @Test
    @DisplayName("CA06: sem a data prevista gravada, ela é calculada como no EstagioService")
    void deveCalcularDataPrevistaQuandoNaoEstaGravada() {
        // arrange: 270 h com 30 h por semana = 9 semanas desde o início, que passa do encerramento em 10 dias
        Turma t8a = turma(2L, "8A", engenharia, hoje.plusDays(10));
        Estagio estagio = estagio(t8a, StatusEstagio.ATIVO, luisa);
        estagio.setDataPrevistaConclusao(null);
        estagio.setDataInicio(hoje.minusWeeks(2));
        estagio.setCargaHorariaNecessaria(new BigDecimal("270"));
        estagio.setJornadaSemanal(new BigDecimal("30"));
        prepararBanco(List.of(estagio), repetir(TIPO_ESTAGIARIO, 3), repetir(estagio.getId(), 3), List.of());

        // act
        PainelCoordenadorResponse painel = painelCoordenadorService.montarPainel(EMAIL, null);

        // assert
        assertEquals(1, painel.getRetaFinalSoHoras());
    }

    @Test
    @DisplayName("CA07: cartão 6, linha 'com as duas pendências' quando falta tarefa e as horas não fecham")
    void deveContarAsDuasPendencias() {
        // arrange
        Turma t8a = turma(2L, "8A", engenharia, hoje.plusDays(5));
        Estagio estagio = estagio(t8a, StatusEstagio.ATIVO, luisa);
        estagio.setDataPrevistaConclusao(hoje.plusDays(25));
        prepararBanco(List.of(estagio), repetir(TIPO_ESTAGIARIO, 3), List.of(), List.of());

        // act
        PainelCoordenadorResponse painel = painelCoordenadorService.montarPainel(EMAIL, null);

        // assert: o estágio entra numa linha só, então as três linhas somam o número do cartão
        assertEquals(1, painel.getEstagiosRetaFinal());
        assertEquals(0, painel.getRetaFinalSoTarefas());
        assertEquals(0, painel.getRetaFinalSoHoras());
        assertEquals(1, painel.getRetaFinalDuasPendencias());
    }

    @Test
    @DisplayName("CA08: cartão 6 fica 0 com todas as tarefas aprovadas e as horas fechando no prazo")
    void naoDeveContarSemPendencia() {
        // arrange
        Turma t8b = turma(1L, "8B", engenharia, hoje.plusDays(15));
        Estagio tudoAprovado = estagio(t8b, StatusEstagio.ATIVO, carlos);
        Estagio horasNoMesmoDia = estagio(t8b, StatusEstagio.ATIVO, carlos);
        horasNoMesmoDia.setDataPrevistaConclusao(hoje.plusDays(15));
        List<Long> aprovadas = new ArrayList<>(repetir(tudoAprovado.getId(), 3));
        aprovadas.addAll(repetir(horasNoMesmoDia.getId(), 3));
        prepararBanco(List.of(tudoAprovado, horasNoMesmoDia), repetir(TIPO_ESTAGIARIO, 3), aprovadas, List.of());

        // act
        PainelCoordenadorResponse painel = painelCoordenadorService.montarPainel(EMAIL, null);

        // assert
        assertEquals(0, painel.getEstagiosRetaFinal());
        assertEquals(0, painel.getRetaFinalSoTarefas());
        assertEquals(0, painel.getRetaFinalSoHoras());
        assertEquals(0, painel.getRetaFinalDuasPendencias());
        assertTrue(painel.getPrazoPorTurma().isEmpty());
    }

    @Test
    @DisplayName("CA08: fora da reta final (mais de 30 dias) e trilha sem tarefas não entram no cartão 6")
    void naoDeveContarForaDaRetaFinalNemTrilhaSemTarefas() {
        // arrange: a 8B tem pendência, mas faltam 31 dias; a 6A está na reta final, mas a trilha não tem tarefas
        Turma longe = turma(1L, "8B", engenharia, hoje.plusDays(31));
        Turma perto = turma(3L, "6A", sistemas, hoje.plusDays(5));
        Estagio estagioLonge = estagio(longe, StatusEstagio.ATIVO, carlos);
        Estagio estagioPerto = estagio(perto, StatusEstagio.ATIVO, carlos);
        estagioPerto.getTipoEstagio().setId(99L);
        prepararBanco(List.of(estagioLonge, estagioPerto), repetir(TIPO_ESTAGIARIO, 3), List.of(), List.of());

        // act
        PainelCoordenadorResponse painel = painelCoordenadorService.montarPainel(EMAIL, null);

        // assert
        assertEquals(0, painel.getEstagiosRetaFinal());
    }

    // ---------- CA09: gráfico 2 ----------

    @Test
    @DisplayName("CA09: gráfico 2 separa por turma, com o orientador, do maior para o menor")
    void deveMontarOGraficoDeCorrecaoPorTurma() {
        // arrange: 8A (Profa. Luísa) com 2, 8B (Prof. Carlos) com 5, 6A (Prof. Carlos) com 1
        Estagio de8a = estagio(turma(2L, "8A", engenharia, hoje.plusDays(90)), StatusEstagio.ATIVO, luisa);
        Estagio de8b = estagio(turma(1L, "8B", engenharia, hoje.plusDays(90)), StatusEstagio.ATIVO, carlos);
        Estagio de6a = estagio(turma(3L, "6A", sistemas, hoje.plusDays(90)), StatusEstagio.ATIVO, carlos);
        List<Entrega> entregas = new ArrayList<>();
        for (int i = 0; i < 2; i++) entregas.add(entrega(de8a));
        for (int i = 0; i < 5; i++) entregas.add(entrega(de8b));
        entregas.add(entrega(de6a));
        prepararBanco(List.of(de8a, de8b, de6a), List.of(), List.of(), entregas);

        // act
        PainelCoordenadorResponse painel = painelCoordenadorService.montarPainel(EMAIL, null);
        List<PainelCoordenadorResponse.CorrecaoTurma> linhas = painel.getCorrecaoPorTurma();

        // assert
        assertEquals(8, painel.getTarefasAguardandoCorrecao());
        assertEquals(3, linhas.size());
        assertEquals("8B", linhas.get(0).getTurma());
        assertEquals("Prof. Carlos", linhas.get(0).getOrientador());
        assertEquals(5, linhas.get(0).getQuantidade());
        assertEquals(100.0, linhas.get(0).getPercentualBarra());
        assertEquals("8A", linhas.get(1).getTurma());
        assertEquals("Profa. Luísa", linhas.get(1).getOrientador());
        assertEquals(40.0, linhas.get(1).getPercentualBarra());
        assertEquals("6A", linhas.get(2).getTurma());
        assertEquals("Sistemas de Informação", linhas.get(2).getCurso());
        assertEquals(20.0, linhas.get(2).getPercentualBarra());
    }

    // ---------- CA10: gráfico 3 ----------

    @Test
    @DisplayName("CA10: gráfico 3 coloca cada turma na faixa certa e a soma das barras é igual ao cartão 6")
    void deveMontarOGraficoDePrazoPorTurma() {
        // arrange: todos com tarefa pendente (nenhuma aprovada)
        Turma t8b = turma(1L, "8B", engenharia, hoje.plusDays(15));
        Turma t8a = turma(2L, "8A", engenharia, hoje.plusDays(5));
        Turma t7a = turma(4L, "7A", engenharia, hoje);
        Turma t6a = turma(3L, "6A", sistemas, hoje.minusDays(3));
        List<Estagio> estagios = List.of(
                estagio(t8b, StatusEstagio.ATIVO, carlos),
                estagio(t8b, StatusEstagio.ATIVO, carlos),
                estagio(t8a, StatusEstagio.ATIVO, luisa),
                estagio(t8a, StatusEstagio.ATIVO, luisa),
                estagio(t7a, StatusEstagio.ATIVO, luisa),
                estagio(t6a, StatusEstagio.ATIVO, carlos));
        prepararBanco(estagios, repetir(TIPO_ESTAGIARIO, 3), List.of(), List.of());

        // act
        PainelCoordenadorResponse painel = painelCoordenadorService.montarPainel(EMAIL, null);
        List<PainelCoordenadorResponse.PrazoTurma> linhas = painel.getPrazoPorTurma();

        // assert: 8B e 8A empatam com 2, e a 8B vem antes porque tem mais dias
        assertEquals(4, linhas.size());
        assertEquals("8B", linhas.get(0).getTurma());
        assertEquals(CalculadoraCargaHoraria.AVISO_AMARELO, linhas.get(0).getFaixa());
        assertEquals("faltam 15 dias", linhas.get(0).getTextoPrazo());
        assertEquals("8A", linhas.get(1).getTurma());
        assertEquals(CalculadoraCargaHoraria.AVISO_VERMELHO, linhas.get(1).getFaixa());
        assertEquals("faltam 5 dias", linhas.get(1).getTextoPrazo());
        assertEquals("7A", linhas.get(2).getTurma());
        assertEquals(CalculadoraCargaHoraria.AVISO_ULTIMO_DIA, linhas.get(2).getFaixa());
        assertEquals("vence hoje", linhas.get(2).getTextoPrazo());
        assertEquals("6A", linhas.get(3).getTurma());
        assertEquals(CalculadoraCargaHoraria.AVISO_ENCERRADO, linhas.get(3).getFaixa());
        assertEquals("venceu há 3 dias", linhas.get(3).getTextoPrazo());
        assertEquals(50.0, linhas.get(3).getPercentualBarra());

        long somaDasBarras = linhas.stream().mapToLong(PainelCoordenadorResponse.PrazoTurma::getQuantidade).sum();
        assertEquals(painel.getEstagiosRetaFinal(), somaDasBarras);
    }

    @Test
    @DisplayName("CA10: o texto do prazo usa singular com 1 dia")
    void deveEscreverOTextoDoPrazoNoSingular() {
        assertEquals("falta 1 dia", PainelCoordenadorService.textoPrazo(1));
        assertEquals("venceu há 1 dia", PainelCoordenadorService.textoPrazo(-1));
    }

    // ---------- CA11: coordenador sem cursos ----------

    @Test
    @DisplayName("CA11: coordenador sem cursos recebe tudo 0 e listas vazias, sem erro")
    void deveDevolverPainelZeradoSemCursos() {
        // arrange
        prepararBanco(List.of(), List.of(), List.of(), List.of());

        // act
        PainelCoordenadorResponse painel = painelCoordenadorService.montarPainel(EMAIL, null);

        // assert
        assertEquals(0, painel.getEstagiosSemOrientador());
        assertEquals(0, painel.getEstagiosAguardandoValidacao());
        assertEquals(0, painel.getEstagiosEmAndamento());
        assertEquals(0, painel.getTurmasSemDataEncerramento());
        assertEquals(0, painel.getTarefasAguardandoCorrecao());
        assertEquals(0, painel.getEstagiosRetaFinal());
        assertTrue(painel.getSituacaoPorTurma().isEmpty());
        assertTrue(painel.getCorrecaoPorTurma().isEmpty());
        assertTrue(painel.getPrazoPorTurma().isEmpty());
    }

    // ---------- CA13: curso de outro coordenador ----------

    @Test
    @DisplayName("CA13: curso de outro coordenador responde 404 e o banco nem é consultado")
    void deveRecusarCursoDeOutroCoordenador() {
        // arrange
        when(cursoService.buscarCursoDoCoordenador(99L, EMAIL))
                .thenThrow(new ResourceNotFoundException("Curso não encontrado"));

        // act + assert
        assertThrows(ResourceNotFoundException.class, () -> painelCoordenadorService.montarPainel(EMAIL, 99L));
        verifyNoInteractions(painelCoordenadorRepository);
    }

    // ---------- CA14: um curso só ----------

    @Test
    @DisplayName("CA14: com um curso escolhido, os números são só daquele curso")
    void deveFiltrarPeloCursoEscolhido() {
        // arrange: um estágio de Engenharia e dois de Sistemas
        when(cursoService.buscarCursoDoCoordenador(1L, EMAIL)).thenReturn(engenharia);
        Estagio deEngenharia = estagio(turma(1L, "8B", engenharia, hoje.plusDays(90)), StatusEstagio.ATIVO, carlos);
        Estagio deSistemas1 = estagio(turma(3L, "6A", sistemas, hoje.plusDays(90)), StatusEstagio.ATIVO, carlos);
        Estagio deSistemas2 = estagio(turma(3L, "6A", sistemas, hoje.plusDays(90)), StatusEstagio.PENDENTE, null);
        prepararBanco(List.of(deEngenharia, deSistemas1, deSistemas2), List.of(), List.of(),
                List.of(entrega(deEngenharia), entrega(deSistemas1)));

        // act
        PainelCoordenadorResponse painel = painelCoordenadorService.montarPainel(EMAIL, 1L);

        // assert
        assertEquals(1, painel.getEstagiosEmAndamento());
        assertEquals(0, painel.getEstagiosSemOrientador());
        assertEquals(1, painel.getTarefasAguardandoCorrecao());
        assertEquals(1, painel.getSituacaoPorTurma().size());
        assertEquals("8B", painel.getSituacaoPorTurma().get(0).getTurma());
        verify(cursoService).buscarCursoDoCoordenador(1L, EMAIL);
    }
}
