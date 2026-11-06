package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tcc.ges.aprovamais.dto.ConsultaCnpjResponse;
import tcc.ges.aprovamais.dto.EstagioCadastroRequest;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.dto.TipoEstagioResponse;
import tcc.ges.aprovamais.entity.Aluno;
import tcc.ges.aprovamais.entity.Curso;
import tcc.ges.aprovamais.entity.Entrega;
import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.Matricula;
import tcc.ges.aprovamais.entity.Orientador;
import tcc.ges.aprovamais.entity.Tarefa;
import tcc.ges.aprovamais.entity.TipoEstagio;
import tcc.ges.aprovamais.entity.Trilha;
import tcc.ges.aprovamais.entity.Turma;
import tcc.ges.aprovamais.entity.ValidacaoEstagio;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.entity.enums.StatusEntrega;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.entity.enums.StatusMatricula;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.external.ConsultaCnpjService;
import tcc.ges.aprovamais.repository.AlunoRepository;
import tcc.ges.aprovamais.repository.EntregaRepository;
import tcc.ges.aprovamais.repository.EstagioRepository;
import tcc.ges.aprovamais.repository.MatriculaRepository;
import tcc.ges.aprovamais.repository.OrientadorTurmaRepository;
import tcc.ges.aprovamais.repository.TarefaRepository;
import tcc.ges.aprovamais.repository.TipoEstagioRepository;
import tcc.ges.aprovamais.repository.TrilhaRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;
import tcc.ges.aprovamais.repository.ValidacaoEstagioRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstagioServiceTest {

    private static final String EMAIL_ALUNO = "aluno@teste.com";
    private static final String EMAIL_ORIENTADOR = "orientador@teste.com";
    private static final String IP = "127.0.0.1";
    private static final Long ESTAGIO_ID = 99L;

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
    private AuditoriaService auditoriaService;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ValidacaoEstagioRepository validacaoEstagioRepository;
    @Mock
    private ConsultaCnpjService consultaCnpjService;
    @Mock
    private TrilhaRepository trilhaRepository;
    @Mock
    private TarefaRepository tarefaRepository;
    @Mock
    private EntregaRepository entregaRepository;
    @InjectMocks
    private EstagioService estagioService;
    private Aluno aluno;
    private Orientador orientador;
    private Matricula matricula;
    private TipoEstagio tipoEstagio;
    private Curso curso;
    private Trilha trilha;

    @BeforeEach
    void setUp() {
        aluno = new Aluno();
        aluno.setId(1L);
        aluno.setNome("Aluno Teste");
        aluno.setEmail(EMAIL_ALUNO);
        aluno.setPerfil(PerfilUsuario.ALUNO);


        orientador = new Orientador();
        orientador.setId(2L);
        orientador.setNome("Orientador Teste");
        orientador.setEmail(EMAIL_ORIENTADOR);
        orientador.setPerfil(PerfilUsuario.ORIENTADOR);



        curso = new Curso();
        curso.setId(4L);

        Turma turma = new Turma();
        turma.setId(3L);
        turma.setCurso(curso);

        matricula = new Matricula();
        matricula.setId(10L);
        matricula.setAluno(aluno);
        matricula.setTurma(turma);
        matricula.setStatus(StatusMatricula.ATIVA);



        tipoEstagio = new TipoEstagio();
        tipoEstagio.setId(5L);
        tipoEstagio.setCurso(curso);
        tipoEstagio.setNome("Estagiário");
        tipoEstagio.setCargaHorariaNecessaria(new BigDecimal("270"));
        tipoEstagio.setJornadaSemanal(new BigDecimal("30"));

        /*
           F2: por padrão a trilha do tipo tem 1 tarefa e o aluno não tem entrega aprovada,
           então existe pendência e os testes do aviso de prazo continuam valendo do jeito que estão.
           lenient porque nem todo teste chega a montar a resposta de um estágio ATIVO
        */
        trilha = new Trilha();
        trilha.setId(6L);
        trilha.setTipoEstagio(tipoEstagio);
        lenient().when(trilhaRepository.findByTipoEstagioId(tipoEstagio.getId())).thenReturn(Optional.of(trilha));
        lenient().when(tarefaRepository.findByTrilhaIdOrderByOrdem(trilha.getId())).thenReturn(criarTarefas(1));
    }

    // Monta um estagio já vinculado à matrícula e ao orientador no status informado
    private Estagio criarEstagio(StatusEstagio status) {
        Estagio estagio = new Estagio();
        estagio.setId(ESTAGIO_ID);
        estagio.setMatricula(matricula);
        estagio.setOrientador(orientador);
        estagio.setTipoEstagio(tipoEstagio);
        estagio.setNomeEmpresa("Empresa Antiga");
        estagio.setDataInicio(LocalDate.of(2026, 8, 1));
        estagio.setCargaHorariaNecessaria(tipoEstagio.getCargaHorariaNecessaria());
        estagio.setJornadaSemanal(tipoEstagio.getJornadaSemanal());
        estagio.setStatus(status);
        return estagio;
    }

    private EstagioCadastroRequest criarRequest() {
        EstagioCadastroRequest request = new EstagioCadastroRequest();
        request.setTipoEstagioId(tipoEstagio.getId());
        request.setNomeEmpresa("Empresa Nova");
        request.setCnpjEmpresa("11.222.333/0001-81");
        request.setDataInicio(LocalDate.of(2026, 10, 1));
        return request;
    }





    private void prepararCadastro(Estagio estagioExistente) {
        when(alunoRepository.findByEmail(EMAIL_ALUNO)).thenReturn(Optional.of(aluno));
        when(matriculaRepository.findFirstByAlunoIdAndStatus(aluno.getId(), StatusMatricula.ATIVA)).thenReturn(Optional.of(matricula));
        when(tipoEstagioRepository.findByIdAndCursoId(tipoEstagio.getId(), curso.getId())).thenReturn(Optional.of(tipoEstagio));
        when(estagioRepository.findFirstByMatriculaIdAndStatusInOrderByIdDesc(matricula.getId(), EstagioService.STATUS_EM_ABERTO))
                .thenReturn(Optional.ofNullable(estagioExistente));
        // lenient porque nos testes que barram antes a consulta nem chega a acontecer
        lenient().when(consultaCnpjService.consultar("11222333000181"))
                .thenReturn(ConsultaCnpjResponse.encontrado("EMPRESA NOVA LTDA", null, "ATIVA"));
    }



    @Test
    @DisplayName("Estágio pendente é aprovado, vira ATIVO e registra auditoria")
    void deveAprovarEstagioPendente() {


        // arrange
        Estagio estagio = criarEstagio(StatusEstagio.PENDENTE);
        when(estagioRepository.findByIdAndOrientadorEmail(ESTAGIO_ID, EMAIL_ORIENTADOR))
                .thenReturn(Optional.of(estagio));
        when(estagioRepository.save(estagio)).then(returnsFirstArg());
        when(usuarioRepository.findByEmail(EMAIL_ORIENTADOR)).thenReturn(Optional.of(orientador));

        // act
        EstagioResponse resposta = estagioService.aprovar(ESTAGIO_ID, EMAIL_ORIENTADOR, null, IP);

        // assert
        assertEquals("ATIVO", resposta.getStatus());
        assertEquals(StatusEstagio.ATIVO, estagio.getStatus());
        assertNull(resposta.getJustificativaRejeicao());
        verify(auditoriaService).registrar(eq(orientador), eq("ESTAGIO_APROVADO"), anyString(), eq(IP), eq(true));

    }

    @Test
    @DisplayName("Estágio já pendente impede o aluno de reenviar a solicitação")
    void deveLancarExcecaoQuandoJaExisteEstagioPendente() {
        // arrange
        prepararCadastro(criarEstagio(StatusEstagio.PENDENTE));

        // act
        IllegalStateException excecao = assertThrows(IllegalStateException.class, () -> estagioService.cadastrarOuAtualizarEstagio(EMAIL_ALUNO, criarRequest(), IP));

        // assert
        assertEquals("Já existe uma solicitação aguardando aprovação do orientador", excecao.getMessage());verify(estagioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Orientador não consegue aprovar estágio que não é dele")
    void deveLancarNaoEncontradoQuandoEstagioEDeOutroOrientador() {
        // consulta filtra por orientador
        when(estagioRepository.findByIdAndOrientadorEmail(ESTAGIO_ID, EMAIL_ORIENTADOR))
                .thenReturn(Optional.empty());

        // act
        ResourceNotFoundException excecao = assertThrows(ResourceNotFoundException.class, () -> estagioService.aprovar(ESTAGIO_ID, EMAIL_ORIENTADOR, null, IP));

        // 404 em vez de 403
        assertEquals("Estágio não encontrado", excecao.getMessage());
        verify(estagioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reenvio após rejeição volta para PENDENTE e limpa a justificativa")
    void deveReenviarEstagioRejeitadoLimpandoJustificativa() {

        // arrange
        Estagio rejeitado = criarEstagio(StatusEstagio.REJEITADO);
        rejeitado.setJustificativaRejeicao("Documentação incompleta");
        prepararCadastro(rejeitado);
        when(orientadorTurmaRepository.findFirstByTurmaId(matricula.getTurma().getId())).thenReturn(Optional.empty());
        when(estagioRepository.save(rejeitado)).then(returnsFirstArg());
        when(estagioRepository.findById(ESTAGIO_ID)).thenReturn(Optional.of(rejeitado));


        // act
        EstagioResponse resposta = estagioService.cadastrarOuAtualizarEstagio(
                EMAIL_ALUNO, criarRequest(), IP);


        // assert
        assertEquals(ESTAGIO_ID, resposta.getId());
        assertEquals("PENDENTE", resposta.getStatus());
        assertEquals("Empresa Nova", resposta.getNomeEmpresa());
        assertNull(resposta.getJustificativaRejeicao());
        assertNull(rejeitado.getJustificativaRejeicao());
        verify(estagioRepository).save(rejeitado);
    }

    @Test
    @DisplayName("Cadastro copia a carga total e a jornada do tipo e calcula a data prevista")
    void deveCongelarCargaEJornadaDoTipoNoCadastro() {
        // arrange
        prepararCadastro(null);
        when(orientadorTurmaRepository.findFirstByTurmaId(matricula.getTurma().getId())).thenReturn(Optional.empty());
        ArgumentCaptor<Estagio> captor = ArgumentCaptor.forClass(Estagio.class);
        when(estagioRepository.save(captor.capture())).thenAnswer(inv -> {
            Estagio salvo = inv.getArgument(0);
            salvo.setId(ESTAGIO_ID);
            return salvo;
        });
        when(estagioRepository.findById(ESTAGIO_ID)).thenAnswer(inv -> Optional.of(captor.getValue()));

        // act
        EstagioResponse resposta = estagioService.cadastrarOuAtualizarEstagio(EMAIL_ALUNO, criarRequest(), IP);

        // assert: 270 h com 30 h por semana são 9 semanas a partir de 01/10/2026
        Estagio salvo = captor.getValue();
        assertEquals(new BigDecimal("270"), salvo.getCargaHorariaNecessaria());
        assertEquals(new BigDecimal("30"), salvo.getJornadaSemanal());
        assertEquals(LocalDate.of(2026, 12, 3), salvo.getDataPrevistaConclusao());
        assertEquals(LocalDate.of(2026, 12, 3), resposta.getDataPrevistaConclusao());

        // mudar o tipo depois não mexe no estágio já cadastrado
        tipoEstagio.setCargaHorariaNecessaria(new BigDecimal("300"));
        tipoEstagio.setJornadaSemanal(new BigDecimal("20"));
        assertEquals(new BigDecimal("270"), salvo.getCargaHorariaNecessaria());
        assertEquals(new BigDecimal("30"), salvo.getJornadaSemanal());
    }

    @Test
    @DisplayName("Tipo sem jornada semanal impede o cadastro do estágio")
    void deveBloquearCadastroQuandoTipoNaoTemJornada() {
        // arrange
        tipoEstagio.setJornadaSemanal(null);
        prepararCadastro(null);

        // act
        IllegalStateException excecao = assertThrows(IllegalStateException.class,
                () -> estagioService.cadastrarOuAtualizarEstagio(EMAIL_ALUNO, criarRequest(), IP));

        // assert
        assertEquals("Este tipo de estágio está sem jornada semanal cadastrada. Procure a coordenação",
                excecao.getMessage());
        verify(estagioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lista só os tipos de estágio do curso da matrícula do aluno")
    void deveListarTiposDoCursoDoAluno() {
        // arrange
        when(alunoRepository.findByEmail(EMAIL_ALUNO)).thenReturn(Optional.of(aluno));
        when(matriculaRepository.findFirstByAlunoIdAndStatus(aluno.getId(), StatusMatricula.ATIVA)).thenReturn(Optional.of(matricula));
        when(tipoEstagioRepository.findByCursoIdOrderByNomeAsc(curso.getId())).thenReturn(List.of(tipoEstagio));

        // act
        List<TipoEstagioResponse> tipos = estagioService.listarTiposEstagioDisponiveis(EMAIL_ALUNO);

        // assert
        assertEquals(1, tipos.size());
        assertEquals("Estagiário", tipos.get(0).getNome());
        assertEquals(new BigDecimal("30"), tipos.get(0).getJornadaSemanal());
        verify(tipoEstagioRepository, never()).findAll();
    }

    @Test
    @DisplayName("Tipo de estágio de outro curso é tratado como inexistente no cadastro")
    void deveBloquearCadastroComTipoDeOutroCurso() {
        // arrange: a busca filtrada pelo curso do aluno não acha o tipo
        when(alunoRepository.findByEmail(EMAIL_ALUNO)).thenReturn(Optional.of(aluno));
        when(matriculaRepository.findFirstByAlunoIdAndStatus(aluno.getId(), StatusMatricula.ATIVA)).thenReturn(Optional.of(matricula));
        when(tipoEstagioRepository.findByIdAndCursoId(tipoEstagio.getId(), curso.getId())).thenReturn(Optional.empty());

        // act
        ResourceNotFoundException excecao = assertThrows(ResourceNotFoundException.class,
                () -> estagioService.cadastrarOuAtualizarEstagio(EMAIL_ALUNO, criarRequest(), IP));

        // assert
        assertEquals("Perfil de estágio não encontrado", excecao.getMessage());
        verify(estagioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Aprovação e rejeição ficam gravadas no histórico de validações")
    void deveRegistrarDecisoesNoHistorico() {
        // arrange
        Estagio estagio = criarEstagio(StatusEstagio.PENDENTE);
        when(estagioRepository.findByIdAndOrientadorEmail(ESTAGIO_ID, EMAIL_ORIENTADOR))
                .thenReturn(Optional.of(estagio));
        when(estagioRepository.save(estagio)).then(returnsFirstArg());
        ArgumentCaptor<ValidacaoEstagio> captor = ArgumentCaptor.forClass(ValidacaoEstagio.class);

        // act: rejeita, o aluno corrige (volta a PENDENTE) e o orientador aprova
        estagioService.rejeitar(ESTAGIO_ID, EMAIL_ORIENTADOR, "Data não confere com o termo", IP);
        estagio.setStatus(StatusEstagio.PENDENTE);
        estagioService.aprovar(ESTAGIO_ID, EMAIL_ORIENTADOR, null, IP);

        // assert: duas linhas no histórico, a justificativa da rejeição continua lá
        verify(validacaoEstagioRepository, times(2)).save(captor.capture());
        ValidacaoEstagio rejeicao = captor.getAllValues().get(0);
        ValidacaoEstagio aprovacao = captor.getAllValues().get(1);
        assertFalse(rejeicao.getAprovado());
        assertEquals("Data não confere com o termo", rejeicao.getJustificativa());
        assertEquals(orientador, rejeicao.getOrientador());
        assertTrue(aprovacao.getAprovado());
        assertNull(aprovacao.getJustificativa());
    }

    @Test
    @DisplayName("Data de início conferida pelo orientador substitui a do aluno e recalcula a data prevista")
    void deveAjustarDataInicioNaAprovacao() {
        // arrange
        Estagio estagio = criarEstagio(StatusEstagio.PENDENTE);
        when(estagioRepository.findByIdAndOrientadorEmail(ESTAGIO_ID, EMAIL_ORIENTADOR))
                .thenReturn(Optional.of(estagio));
        when(estagioRepository.save(estagio)).then(returnsFirstArg());

        // act
        EstagioResponse resposta = estagioService.aprovar(
                ESTAGIO_ID, EMAIL_ORIENTADOR, LocalDate.of(2026, 3, 2), IP);

        // assert: 270 h com 30 h por semana, 9 semanas depois de 02/03
        assertEquals(LocalDate.of(2026, 3, 2), estagio.getDataInicio());
        assertEquals(LocalDate.of(2026, 5, 4), estagio.getDataPrevistaConclusao());
        assertEquals(LocalDate.of(2026, 5, 4), resposta.getDataPrevistaConclusao());
    }

    @Test
    @DisplayName("Estágio ATIVO não pode ser reenviado, o caminho é a troca de modalidade")
    void deveBloquearReenvioDeEstagioAtivo() {
        // arrange
        Estagio ativo = criarEstagio(StatusEstagio.ATIVO);
        prepararCadastro(ativo);

        // act
        IllegalStateException excecao = assertThrows(IllegalStateException.class,
                () -> estagioService.cadastrarOuAtualizarEstagio(EMAIL_ALUNO, criarRequest(), IP));

        // assert
        assertTrue(excecao.getMessage().contains("troca de modalidade"));
        assertEquals(StatusEstagio.ATIVO, ativo.getStatus());
        assertEquals("Empresa Antiga", ativo.getNomeEmpresa());
        verify(estagioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Com a BrasilAPI fora do ar, o estágio é cadastrado sem a situação do CNPJ")
    void deveCadastrarSemSituacaoQuandoConsultaIndisponivel() {
        // arrange
        prepararCadastro(null);
        when(consultaCnpjService.consultar("11222333000181")).thenReturn(ConsultaCnpjResponse.indisponivel());
        when(orientadorTurmaRepository.findFirstByTurmaId(matricula.getTurma().getId())).thenReturn(Optional.empty());
        ArgumentCaptor<Estagio> captor = ArgumentCaptor.forClass(Estagio.class);
        when(estagioRepository.save(captor.capture())).thenAnswer(inv -> {
            Estagio salvo = inv.getArgument(0);
            salvo.setId(ESTAGIO_ID);
            return salvo;
        });
        when(estagioRepository.findById(ESTAGIO_ID)).thenAnswer(inv -> Optional.of(captor.getValue()));

        // act
        EstagioResponse resposta = estagioService.cadastrarOuAtualizarEstagio(EMAIL_ALUNO, criarRequest(), IP);

        // assert CNPJ gravado sem pontuação, situação vazia pro orientador conferir
        assertEquals("11222333000181", captor.getValue().getCnpjEmpresa());
        assertNull(captor.getValue().getSituacaoCnpj());
        assertEquals("11.222.333/0001-81", resposta.getCnpjEmpresa());
    }

    @Test
    @DisplayName("CNPJ que a Receita não conhece impede o cadastro")
    void deveBloquearCadastroComCnpjInexistente() {
        // arrange
        prepararCadastro(null);
        when(consultaCnpjService.consultar("11222333000181")).thenReturn(ConsultaCnpjResponse.naoEncontrado());

        // act
        IllegalStateException excecao = assertThrows(IllegalStateException.class,
                () -> estagioService.cadastrarOuAtualizarEstagio(EMAIL_ALUNO, criarRequest(), IP));

        // assert
        assertEquals("CNPJ não encontrado na Receita Federal. Confira o número digitado", excecao.getMessage());
        verify(estagioRepository, never()).save(any());
    }

    // F2: horas restantes, data de encerramento das entregas e aviso de prazo

    // Prepara a consulta do aluno devolvendo o estágio informado como o estágio em aberto da matrícula
    private void prepararConsultaDoAluno(Estagio estagio) {
        when(alunoRepository.findByEmail(EMAIL_ALUNO)).thenReturn(Optional.of(aluno));
        when(matriculaRepository.findFirstByAlunoIdAndStatus(aluno.getId(), StatusMatricula.ATIVA)).thenReturn(Optional.of(matricula));
        when(estagioRepository.findFirstByMatriculaIdAndStatusInOrderByIdDesc(matricula.getId(), EstagioService.STATUS_EM_ABERTO))
                .thenReturn(Optional.of(estagio));
    }

    @Test
    @DisplayName("Estágio ativo mostra as horas restantes e o aviso amarelo quando faltam 20 dias para o encerramento das entregas")
    void deveMostrarHorasRestantesEAvisoAmarelo() {
        // arrange: 3 semanas completas de 30 h (90 h) e encerramento das entregas daqui a 20 dias
        Estagio estagio = criarEstagio(StatusEstagio.ATIVO);
        estagio.setDataInicio(LocalDate.now().minusWeeks(3));
        matricula.getTurma().setDataEncerramentoEntregas(LocalDate.now().plusDays(20));
        prepararConsultaDoAluno(estagio);

        // act
        EstagioResponse resposta = estagioService.buscarEstagioDoAluno(EMAIL_ALUNO).orElseThrow();

        // assert
        assertEquals(new BigDecimal("90"), resposta.getCargaHorariaCumprida());
        assertEquals(new BigDecimal("180"), resposta.getCargaHorariaRestante());
        assertEquals(LocalDate.now().plusDays(20), resposta.getDataEncerramentoEntregas());
        assertEquals(20L, resposta.getDiasParaEncerramentoEntregas());
        assertEquals(CalculadoraCargaHoraria.AVISO_AMARELO, resposta.getAvisoPrazo());
    }

    @Test
    @DisplayName("Estágio ativo mostra o aviso vermelho quando faltam 5 dias para o encerramento das entregas")
    void deveMostrarAvisoVermelho() {
        // arrange
        Estagio estagio = criarEstagio(StatusEstagio.ATIVO);
        matricula.getTurma().setDataEncerramentoEntregas(LocalDate.now().plusDays(5));
        prepararConsultaDoAluno(estagio);

        // act
        EstagioResponse resposta = estagioService.buscarEstagioDoAluno(EMAIL_ALUNO).orElseThrow();

        // assert
        assertEquals(5L, resposta.getDiasParaEncerramentoEntregas());
        assertEquals(CalculadoraCargaHoraria.AVISO_VERMELHO, resposta.getAvisoPrazo());
    }

    @Test
    @DisplayName("Estágio pendente mostra a data de encerramento, mas não recebe aviso de prazo")
    void naoDeveMostrarAvisoParaEstagioPendente() {
        // arrange
        Estagio estagio = criarEstagio(StatusEstagio.PENDENTE);
        matricula.getTurma().setDataEncerramentoEntregas(LocalDate.now().plusDays(5));
        prepararConsultaDoAluno(estagio);

        // act
        EstagioResponse resposta = estagioService.buscarEstagioDoAluno(EMAIL_ALUNO).orElseThrow();

        // assert
        assertEquals(5L, resposta.getDiasParaEncerramentoEntregas());
        assertNull(resposta.getAvisoPrazo());
    }

    @Test
    @DisplayName("Turma sem data de encerramento das entregas não gera aviso nem erro")
    void naoDeveQuebrarQuandoTurmaNaoTemDataDeEncerramento() {
        // arrange: a turma do setUp foi criada sem a data, como as turmas antigas
        Estagio estagio = criarEstagio(StatusEstagio.ATIVO);
        prepararConsultaDoAluno(estagio);

        // act
        EstagioResponse resposta = estagioService.buscarEstagioDoAluno(EMAIL_ALUNO).orElseThrow();

        // assert
        assertNull(resposta.getDataEncerramentoEntregas());
        assertNull(resposta.getDiasParaEncerramentoEntregas());
        assertNull(resposta.getAvisoPrazo());
    }

    @Test
    @DisplayName("Orientador recebe só os estágios ATIVO dele, já com o aviso de prazo calculado")
    void deveListarEstagiosAtivosDoOrientador() {
        // arrange
        Estagio estagio = criarEstagio(StatusEstagio.ATIVO);
        matricula.getTurma().setDataEncerramentoEntregas(LocalDate.now().plusDays(20));
        when(estagioRepository.findByOrientadorEmailAndStatusIn(EMAIL_ORIENTADOR, List.of(StatusEstagio.ATIVO)))
                .thenReturn(List.of(estagio));

        // act
        List<EstagioResponse> ativos = estagioService.listarAtivosDoOrientador(EMAIL_ORIENTADOR);

        // assert
        assertEquals(1, ativos.size());
        assertEquals("Aluno Teste", ativos.get(0).getNomeAluno());
        assertEquals(CalculadoraCargaHoraria.AVISO_AMARELO, ativos.get(0).getAvisoPrazo());
    }

    // F2: atividades da trilha aprovadas e restantes, e o aviso de prazo só enquanto houver pendência

    // Monta a quantidade informada de tarefas da trilha, na ordem 1, 2, 3...
    private List<Tarefa> criarTarefas(int quantidade) {
        List<Tarefa> tarefas = new ArrayList<>();
        for (int ordem = 1; ordem <= quantidade; ordem++) {
            Tarefa tarefa = new Tarefa();
            tarefa.setId(100L + ordem);
            tarefa.setTrilha(trilha);
            tarefa.setOrdem(ordem);
            tarefas.add(tarefa);
        }
        return tarefas;
    }

    // Monta uma entrega do estágio com o status informado
    private Entrega criarEntrega(Estagio estagio, StatusEntrega status) {
        Entrega entrega = new Entrega();
        entrega.setEstagio(estagio);
        entrega.setStatus(status);
        return entrega;
    }

    @Test
    @DisplayName("Trilha com 3 tarefas e 1 entrega aprovada mostra 1 aprovada e 2 restantes, e a entrega em revisão não conta")
    void deveContarAtividadesAprovadasERestantes() {
        // arrange: 3 tarefas, uma entrega APROVADA e outra EM_REVISAO, faltando 20 dias para o encerramento
        Estagio estagio = criarEstagio(StatusEstagio.ATIVO);
        matricula.getTurma().setDataEncerramentoEntregas(LocalDate.now().plusDays(20));
        when(tarefaRepository.findByTrilhaIdOrderByOrdem(trilha.getId())).thenReturn(criarTarefas(3));
        when(entregaRepository.findByEstagioId(ESTAGIO_ID)).thenReturn(List.of(
                criarEntrega(estagio, StatusEntrega.APROVADA),
                criarEntrega(estagio, StatusEntrega.EM_REVISAO)));
        prepararConsultaDoAluno(estagio);

        // act
        EstagioResponse resposta = estagioService.buscarEstagioDoAluno(EMAIL_ALUNO).orElseThrow();

        // assert
        assertEquals(1L, resposta.getAtividadesAprovadas());
        assertEquals(3L, resposta.getAtividadesTotal());
        assertEquals(2L, resposta.getAtividadesRestantes());
        assertEquals(CalculadoraCargaHoraria.AVISO_AMARELO, resposta.getAvisoPrazo());
    }

    @Test
    @DisplayName("Com todas as atividades aprovadas, faltando 5 dias, não aparece aviso de prazo")
    void naoDeveMostrarAvisoComTodasAtividadesAprovadas() {
        // arrange
        Estagio estagio = criarEstagio(StatusEstagio.ATIVO);
        matricula.getTurma().setDataEncerramentoEntregas(LocalDate.now().plusDays(5));
        when(tarefaRepository.findByTrilhaIdOrderByOrdem(trilha.getId())).thenReturn(criarTarefas(2));
        when(entregaRepository.findByEstagioId(ESTAGIO_ID)).thenReturn(List.of(
                criarEntrega(estagio, StatusEntrega.APROVADA),
                criarEntrega(estagio, StatusEntrega.APROVADA)));
        prepararConsultaDoAluno(estagio);

        // act
        EstagioResponse resposta = estagioService.buscarEstagioDoAluno(EMAIL_ALUNO).orElseThrow();

        // assert: a data e os dias continuam na resposta, só o aviso some
        assertEquals(5L, resposta.getDiasParaEncerramentoEntregas());
        assertEquals(0L, resposta.getAtividadesRestantes());
        assertNull(resposta.getAvisoPrazo());
    }

    @Test
    @DisplayName("Faltando 10 dias com 1 atividade pendente, o aviso é vermelho")
    void deveMostrarAvisoVermelhoCom10DiasEAtividadePendente() {
        // arrange: a trilha do setUp tem 1 tarefa e o aluno não tem entrega aprovada
        Estagio estagio = criarEstagio(StatusEstagio.ATIVO);
        matricula.getTurma().setDataEncerramentoEntregas(LocalDate.now().plusDays(10));
        prepararConsultaDoAluno(estagio);

        // act
        EstagioResponse resposta = estagioService.buscarEstagioDoAluno(EMAIL_ALUNO).orElseThrow();

        // assert
        assertEquals(1L, resposta.getAtividadesRestantes());
        assertEquals(CalculadoraCargaHoraria.AVISO_VERMELHO, resposta.getAvisoPrazo());
    }

    @Test
    @DisplayName("Com a data de encerramento de ontem e 1 atividade pendente, o aviso é de prazo vencido")
    void deveMostrarPrazoVencidoComAtividadePendente() {
        // arrange
        Estagio estagio = criarEstagio(StatusEstagio.ATIVO);
        matricula.getTurma().setDataEncerramentoEntregas(LocalDate.now().minusDays(1));
        prepararConsultaDoAluno(estagio);

        // act
        EstagioResponse resposta = estagioService.buscarEstagioDoAluno(EMAIL_ALUNO).orElseThrow();

        // assert: os dias vêm negativos, e a tela mostra "há 1 dia"
        assertEquals(-1L, resposta.getDiasParaEncerramentoEntregas());
        assertEquals(CalculadoraCargaHoraria.AVISO_ENCERRADO, resposta.getAvisoPrazo());
    }

    @Test
    @DisplayName("Trilha sem tarefas não tem atividade pendente, então não aparece aviso de prazo")
    void naoDeveMostrarAvisoComTrilhaSemTarefas() {
        // arrange
        Estagio estagio = criarEstagio(StatusEstagio.ATIVO);
        matricula.getTurma().setDataEncerramentoEntregas(LocalDate.now().plusDays(20));
        when(tarefaRepository.findByTrilhaIdOrderByOrdem(trilha.getId())).thenReturn(List.of());
        prepararConsultaDoAluno(estagio);

        // act
        EstagioResponse resposta = estagioService.buscarEstagioDoAluno(EMAIL_ALUNO).orElseThrow();

        // assert: a data de encerramento continua, mas sem aviso
        assertEquals(0L, resposta.getAtividadesTotal());
        assertEquals(LocalDate.now().plusDays(20), resposta.getDataEncerramentoEntregas());
        assertNull(resposta.getAvisoPrazo());
    }

    @Test
    @DisplayName("Tipo de estágio sem trilha cadastrada conta zero atividades e não mostra aviso de prazo")
    void naoDeveMostrarAvisoQuandoTipoNaoTemTrilha() {
        // arrange
        Estagio estagio = criarEstagio(StatusEstagio.ATIVO);
        matricula.getTurma().setDataEncerramentoEntregas(LocalDate.now().plusDays(20));
        when(trilhaRepository.findByTipoEstagioId(tipoEstagio.getId())).thenReturn(Optional.empty());
        prepararConsultaDoAluno(estagio);

        // act
        EstagioResponse resposta = estagioService.buscarEstagioDoAluno(EMAIL_ALUNO).orElseThrow();

        // assert
        assertEquals(0L, resposta.getAtividadesTotal());
        assertNull(resposta.getAvisoPrazo());
    }

    @Test
    @DisplayName("Depois de atingir a carga, as semanas param nas semanas necessárias: 270 h com 30 h por semana são 9")
    void deveLimitarSemanasContadasAsSemanasNecessarias() {
        // arrange: começou há 20 semanas, mas 9 semanas de 30 h já cumprem as 270 h
        Estagio estagio = criarEstagio(StatusEstagio.ATIVO);
        estagio.setDataInicio(LocalDate.now().minusWeeks(20));
        prepararConsultaDoAluno(estagio);

        // act
        EstagioResponse resposta = estagioService.buscarEstagioDoAluno(EMAIL_ALUNO).orElseThrow();

        // assert
        assertEquals(9L, resposta.getSemanasContadas());
        assertEquals(new BigDecimal("270"), resposta.getCargaHorariaCumprida());
        assertEquals(new BigDecimal("0"), resposta.getCargaHorariaRestante());
    }

    @Test
    @DisplayName("Antes de atingir a carga, as semanas continuam sendo contadas normalmente")
    void naoDeveLimitarSemanasAntesDeAtingirACarga() {
        // arrange: 3 semanas de 30 h são 90 h, ainda longe das 270 h
        Estagio estagio = criarEstagio(StatusEstagio.ATIVO);
        estagio.setDataInicio(LocalDate.now().minusWeeks(3));
        prepararConsultaDoAluno(estagio);

        // act
        EstagioResponse resposta = estagioService.buscarEstagioDoAluno(EMAIL_ALUNO).orElseThrow();

        // assert
        assertEquals(3L, resposta.getSemanasContadas());
    }

    @Test
    @DisplayName("Estágio pendente não conta as atividades, porque o aluno ainda não faz entregas")
    void naoDeveContarAtividadesParaEstagioPendente() {
        // arrange
        Estagio estagio = criarEstagio(StatusEstagio.PENDENTE);
        prepararConsultaDoAluno(estagio);

        // act
        EstagioResponse resposta = estagioService.buscarEstagioDoAluno(EMAIL_ALUNO).orElseThrow();

        // assert
        assertNull(resposta.getAtividadesAprovadas());
        assertNull(resposta.getAtividadesTotal());
        assertNull(resposta.getAtividadesRestantes());
        verify(entregaRepository, never()).findByEstagioId(any());
    }
}
