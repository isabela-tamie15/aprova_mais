package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tcc.ges.aprovamais.dto.SolicitacaoRequest;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.*;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.external.EmailService;
import tcc.ges.aprovamais.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SolicitacaoServiceTest {

    private static final String EMAIL_ALUNO = "aluno@teste.com";
    private static final String EMAIL_SECRETARIA = "secretaria@teste.com";
    private static final String EMAIL_COORDENADOR = "coordenador@teste.com";
    private static final String IP = "127.0.0.1";
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Mock
    private SolicitacaoRepository solicitacaoRepository;
    @Mock
    private EstagioRepository estagioRepository;
    @Mock
    private MatriculaRepository matriculaRepository;
    @Mock
    private AlunoRepository alunoRepository;
    @Mock
    private TipoEstagioRepository tipoEstagioRepository;
    @Mock
    private OrientadorTurmaRepository orientadorTurmaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private EstagioService estagioService;
    @Mock
    private CursoService cursoService;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private EmailService emailService;
    @InjectMocks
    private SolicitacaoService solicitacaoService;

    private Aluno aluno;
    private Matricula matricula;
    private Estagio estagio;
    private TipoEstagio tipoAtual;
    private TipoEstagio tipoNovo;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(solicitacaoService, "appUrl", "http://localhost:8080");

        aluno = new Aluno();
        aluno.setId(1L);
        aluno.setNome("Aluno Teste");
        aluno.setEmail(EMAIL_ALUNO);

        Curso curso = new Curso();
        curso.setId(3L);
        curso.setNome("Engenharia de Software");

        Turma turma = new Turma();
        turma.setId(5L);
        turma.setCurso(curso);
        turma.setIdentificador("8B");
        turma.setSemestre("2026.2");

        matricula = new Matricula();
        matricula.setId(2L);
        matricula.setAluno(aluno);
        matricula.setTurma(turma);
        matricula.setStatus(StatusMatricula.ATIVA);

        tipoAtual = tipo(7L, "CLT", "270", "44");
        tipoNovo = tipo(8L, "Empreendedor", "270", "30");

        estagio = new Estagio();
        estagio.setId(10L);
        estagio.setMatricula(matricula);
        estagio.setTipoEstagio(tipoAtual);
        estagio.setNomeEmpresa("Empresa");
        estagio.setDataInicio(LocalDate.of(2026, 8, 3));
        estagio.setCargaHorariaNecessaria(new BigDecimal("270"));
        estagio.setJornadaSemanal(new BigDecimal("44"));
        estagio.setStatus(StatusEstagio.ATIVO);
    }

    private TipoEstagio tipo(Long id, String nome, String carga, String jornada) {
        TipoEstagio tipo = new TipoEstagio();
        tipo.setId(id);
        tipo.setNome(nome);
        tipo.setCargaHorariaNecessaria(new BigDecimal(carga));
        tipo.setJornadaSemanal(new BigDecimal(jornada));
        return tipo;
    }

    private SolicitacaoRequest pedido(TipoSolicitacao tipo, Long novoTipoId) {
        SolicitacaoRequest request = new SolicitacaoRequest();
        request.setTipo(tipo);
        request.setNovoTipoEstagioId(novoTipoId);
        request.setMotivo(" Motivo do pedido ");
        return request;
    }

    private Solicitacao solicitacao(TipoSolicitacao tipo, StatusSolicitacao status) {
        Solicitacao solicitacao = new Solicitacao();
        solicitacao.setId(40L);
        solicitacao.setEstagio(estagio);
        solicitacao.setTipo(tipo);
        solicitacao.setStatus(status);
        solicitacao.setMotivo("Motivo");
        solicitacao.setCriadoEm(OffsetDateTime.now(FUSO));
        return solicitacao;
    }

    private void alunoComEstagioEmAberto() {
        when(alunoRepository.findByEmail(EMAIL_ALUNO)).thenReturn(Optional.of(aluno));
        when(estagioService.buscarMatriculaAtual(aluno)).thenReturn(matricula);
        when(estagioRepository.findFirstByMatriculaIdAndStatusInOrderByIdDesc(eq(2L), any())).thenReturn(Optional.of(estagio));
    }

    private void salvarDevolveOMesmo() {
        when(solicitacaoRepository.save(any(Solicitacao.class))).thenAnswer(chamada -> chamada.getArgument(0));
    }

    @Test
    @DisplayName("A troca de modalidade vai direto para a coordenação")
    void deveAbrirTrocaNaCoordenacao() {
        alunoComEstagioEmAberto();
        when(tipoEstagioRepository.findByIdAndCursoId(8L, 3L)).thenReturn(Optional.of(tipoNovo));
        salvarDevolveOMesmo();

        var resposta = solicitacaoService.criar(EMAIL_ALUNO, pedido(TipoSolicitacao.TROCA_MODALIDADE, 8L), IP);

        assertEquals("EM_ANALISE_COORDENACAO", resposta.getStatus());
        assertEquals("Motivo do pedido", resposta.getMotivo());
        verify(auditoriaService).registrar(eq(aluno), eq("SOLICITACAO_CRIADA"), anyString(), eq(IP), eq(true));
    }

    @Test
    @DisplayName("Recusa um segundo pedido enquanto outro está em análise")
    void naoDeveAbrirDoisPedidos() {
        alunoComEstagioEmAberto();
        when(solicitacaoRepository.existsByEstagioIdAndStatusIn(eq(10L), any())).thenReturn(true);

        assertThrows(RegraNegocioException.class,
                () -> solicitacaoService.criar(EMAIL_ALUNO, pedido(TipoSolicitacao.TRANCAMENTO, null), IP));
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Recusa a troca para o mesmo tipo de estágio")
    void naoDeveTrocarParaOMesmoTipo() {
        alunoComEstagioEmAberto();
        when(tipoEstagioRepository.findByIdAndCursoId(7L, 3L)).thenReturn(Optional.of(tipoAtual));

        assertThrows(RegraNegocioException.class,
                () -> solicitacaoService.criar(EMAIL_ALUNO, pedido(TipoSolicitacao.TROCA_MODALIDADE, 7L), IP));
    }

    @Test
    @DisplayName("Depois da secretaria, o trancamento aguarda a coordenação e o estágio continua ativo")
    void secretariaDeveValidarTrancamento() {
        Solicitacao trancamento = solicitacao(TipoSolicitacao.TRANCAMENTO, StatusSolicitacao.PENDENTE);
        when(usuarioRepository.findByEmail(EMAIL_SECRETARIA)).thenReturn(Optional.of(new Secretaria()));
        when(solicitacaoRepository.findById(40L)).thenReturn(Optional.of(trancamento));
        salvarDevolveOMesmo();

        solicitacaoService.aprovarNaSecretaria(40L, EMAIL_SECRETARIA, IP);

        assertEquals(StatusSolicitacao.EM_ANALISE_COORDENACAO, trancamento.getStatus());
        assertEquals(StatusEstagio.ATIVO, estagio.getStatus());
        assertNotNull(trancamento.getAnalisadaSecretariaEm());
    }

    @Test
    @DisplayName("A aprovação final tranca a matrícula, suspende o estágio e abre o prazo de 7 dias")
    void coordenacaoDeveAprovarTrancamento() {
        Solicitacao trancamento = solicitacao(TipoSolicitacao.TRANCAMENTO, StatusSolicitacao.EM_ANALISE_COORDENACAO);
        when(usuarioRepository.findByEmail(EMAIL_COORDENADOR)).thenReturn(Optional.of(new Coordenador()));
        when(solicitacaoRepository.findByIdAndEstagioMatriculaTurmaCursoCoordenadorEmail(40L, EMAIL_COORDENADOR))
                .thenReturn(Optional.of(trancamento));
        salvarDevolveOMesmo();

        solicitacaoService.aprovarNaCoordenacao(40L, EMAIL_COORDENADOR, IP);

        LocalDate hoje = LocalDate.now(FUSO);
        assertEquals(StatusSolicitacao.APROVADA, trancamento.getStatus());
        assertEquals(hoje.plusDays(7), trancamento.getPrazoArrependimento());
        assertEquals(StatusMatricula.TRANCADA, matricula.getStatus());
        assertEquals(StatusEstagio.SUSPENSO, estagio.getStatus());
        assertEquals(hoje, estagio.getSuspensoDesde());
    }

    @Test
    @DisplayName("A troca aprovada encerra o estágio atual e cria um novo pendente do novo tipo")
    void coordenacaoDeveAprovarTroca() {
        Solicitacao troca = solicitacao(TipoSolicitacao.TROCA_MODALIDADE, StatusSolicitacao.EM_ANALISE_COORDENACAO);
        troca.setNovoTipoEstagio(tipoNovo);
        when(usuarioRepository.findByEmail(EMAIL_COORDENADOR)).thenReturn(Optional.of(new Coordenador()));
        when(solicitacaoRepository.findByIdAndEstagioMatriculaTurmaCursoCoordenadorEmail(40L, EMAIL_COORDENADOR))
                .thenReturn(Optional.of(troca));
        salvarDevolveOMesmo();

        solicitacaoService.aprovarNaCoordenacao(40L, EMAIL_COORDENADOR, IP);

        assertEquals(StatusEstagio.ENCERRADO, estagio.getStatus());
        assertEquals(MotivoEncerramento.TROCA_MODALIDADE, estagio.getMotivoEncerramento());
        ArgumentCaptor<Estagio> salvos = ArgumentCaptor.forClass(Estagio.class);
        verify(estagioRepository, times(2)).save(salvos.capture());
        Estagio novo = salvos.getAllValues().get(1);
        assertEquals(StatusEstagio.PENDENTE, novo.getStatus());
        assertEquals(tipoNovo, novo.getTipoEstagio());
        assertEquals(new BigDecimal("30"), novo.getJornadaSemanal());
        assertSame(matricula, novo.getMatricula());
    }

    @Test
    @DisplayName("Não cancela um pedido já aprovado")
    void naoDeveCancelarPedidoAprovado() {
        Solicitacao aprovada = solicitacao(TipoSolicitacao.TRANCAMENTO, StatusSolicitacao.APROVADA);
        when(alunoRepository.findByEmail(EMAIL_ALUNO)).thenReturn(Optional.of(aluno));
        when(estagioService.buscarMatriculaAtual(aluno)).thenReturn(matricula);
        when(solicitacaoRepository.findByIdAndEstagioMatriculaId(40L, 2L)).thenReturn(Optional.of(aprovada));

        assertThrows(RegraNegocioException.class, () -> solicitacaoService.cancelar(40L, EMAIL_ALUNO, IP));
    }

    @Test
    @DisplayName("Cancela um trancamento em análise e o estágio continua ativo")
    void deveCancelarPedidoEmAnalise() {
        Solicitacao emAnalise = solicitacao(TipoSolicitacao.TRANCAMENTO, StatusSolicitacao.EM_ANALISE_COORDENACAO);
        when(alunoRepository.findByEmail(EMAIL_ALUNO)).thenReturn(Optional.of(aluno));
        when(estagioService.buscarMatriculaAtual(aluno)).thenReturn(matricula);
        when(solicitacaoRepository.findByIdAndEstagioMatriculaId(40L, 2L)).thenReturn(Optional.of(emAnalise));
        salvarDevolveOMesmo();

        solicitacaoService.cancelar(40L, EMAIL_ALUNO, IP);

        assertEquals(StatusSolicitacao.CANCELADA, emAnalise.getStatus());
        assertEquals(StatusEstagio.ATIVO, estagio.getStatus());
    }

    @Test
    @DisplayName("A reabertura aprovada volta o estágio a ativo e desconta os dias de suspensão até o pedido")
    void secretariaDeveAprovarReabertura() {
        LocalDate hoje = LocalDate.now(FUSO);
        estagio.setStatus(StatusEstagio.SUSPENSO);
        estagio.setSuspensoDesde(hoje.minusDays(9));
        matricula.setStatus(StatusMatricula.TRANCADA);
        Solicitacao reabertura = solicitacao(TipoSolicitacao.REABERTURA, StatusSolicitacao.PENDENTE);
        reabertura.setCriadoEm(OffsetDateTime.now(FUSO).minusDays(3));
        when(usuarioRepository.findByEmail(EMAIL_SECRETARIA)).thenReturn(Optional.of(new Secretaria()));
        when(solicitacaoRepository.findById(40L)).thenReturn(Optional.of(reabertura));
        salvarDevolveOMesmo();

        solicitacaoService.aprovarNaSecretaria(40L, EMAIL_SECRETARIA, IP);

        assertEquals(StatusEstagio.ATIVO, estagio.getStatus());
        assertEquals(StatusMatricula.ATIVA, matricula.getStatus());
        assertEquals(6, estagio.getDiasSuspensos());
        assertNull(estagio.getSuspensoDesde());
        // 270 h com 44 h semanais são 7 semanas, mais os 6 dias de suspensão
        assertEquals(LocalDate.of(2026, 8, 3).plusDays(6).plusWeeks(7), estagio.getDataPrevistaConclusao());
    }

    @Test
    @DisplayName("Uma reabertura reprovada depois do prazo torna o trancamento definitivo na hora")
    void reprovacaoDepoisDoPrazoDeveEncerrarEstagio() {
        estagio.setStatus(StatusEstagio.SUSPENSO);
        estagio.setSuspensoDesde(LocalDate.now(FUSO).minusDays(10));
        Solicitacao reabertura = solicitacao(TipoSolicitacao.REABERTURA, StatusSolicitacao.PENDENTE);
        Solicitacao trancamento = solicitacao(TipoSolicitacao.TRANCAMENTO, StatusSolicitacao.APROVADA);
        trancamento.setPrazoArrependimento(LocalDate.now(FUSO).minusDays(3));
        when(usuarioRepository.findByEmail(EMAIL_SECRETARIA)).thenReturn(Optional.of(new Secretaria()));
        when(solicitacaoRepository.findById(40L)).thenReturn(Optional.of(reabertura));
        when(solicitacaoRepository.findFirstByEstagioIdAndTipoAndStatusOrderByIdDesc(
                10L, TipoSolicitacao.TRANCAMENTO, StatusSolicitacao.APROVADA)).thenReturn(Optional.of(trancamento));
        salvarDevolveOMesmo();

        solicitacaoService.reprovarNaSecretaria(40L, EMAIL_SECRETARIA, "Documentação incompleta", IP);

        assertEquals(StatusSolicitacao.REPROVADA, reabertura.getStatus());
        assertEquals(StatusEstagio.ENCERRADO, estagio.getStatus());
        assertEquals(MotivoEncerramento.TRANCAMENTO, estagio.getMotivoEncerramento());
    }

    @Test
    @DisplayName("Recusa a reabertura pedida depois do prazo")
    void naoDeveAbrirReaberturaForaDoPrazo() {
        estagio.setStatus(StatusEstagio.SUSPENSO);
        alunoComEstagioEmAberto();
        Solicitacao trancamento = solicitacao(TipoSolicitacao.TRANCAMENTO, StatusSolicitacao.APROVADA);
        trancamento.setPrazoArrependimento(LocalDate.now(FUSO).minusDays(1));
        when(solicitacaoRepository.findFirstByEstagioIdAndTipoAndStatusOrderByIdDesc(
                10L, TipoSolicitacao.TRANCAMENTO, StatusSolicitacao.APROVADA)).thenReturn(Optional.of(trancamento));

        assertThrows(RegraNegocioException.class,
                () -> solicitacaoService.criar(EMAIL_ALUNO, pedido(TipoSolicitacao.REABERTURA, null), IP));
    }

    @Test
    @DisplayName("A rotina diária encerra o trancamento vencido sem reabertura e preserva o que tem pedido em análise")
    void rotinaDeveEfetivarSoOsVencidosSemPedido() {
        estagio.setStatus(StatusEstagio.SUSPENSO);
        Estagio comPedido = new Estagio();
        comPedido.setId(11L);
        comPedido.setMatricula(matricula);
        comPedido.setStatus(StatusEstagio.SUSPENSO);

        Solicitacao vencido = solicitacao(TipoSolicitacao.TRANCAMENTO, StatusSolicitacao.APROVADA);
        Solicitacao vencidoComPedido = solicitacao(TipoSolicitacao.TRANCAMENTO, StatusSolicitacao.APROVADA);
        vencidoComPedido.setEstagio(comPedido);
        when(solicitacaoRepository.findByTipoAndStatusAndPrazoArrependimentoBeforeAndEstagioStatus(
                eq(TipoSolicitacao.TRANCAMENTO), eq(StatusSolicitacao.APROVADA), any(), eq(StatusEstagio.SUSPENSO)))
                .thenReturn(List.of(vencido, vencidoComPedido));
        when(solicitacaoRepository.existsByEstagioIdAndStatusIn(eq(10L), any())).thenReturn(false);
        when(solicitacaoRepository.existsByEstagioIdAndStatusIn(eq(11L), any())).thenReturn(true);

        solicitacaoService.efetivarTrancamentosVencidos();

        assertEquals(StatusEstagio.ENCERRADO, estagio.getStatus());
        assertEquals(MotivoEncerramento.TRANCAMENTO, estagio.getMotivoEncerramento());
        assertEquals(StatusEstagio.SUSPENSO, comPedido.getStatus());
        verify(auditoriaService).registrar(eq(aluno), eq("TRANCAMENTO_EFETIVADO"), anyString(), any(), eq(true));
    }
}
