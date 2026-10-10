package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tcc.ges.aprovamais.dto.EstagioCadastroRequest;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.entity.Aluno;
import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.Matricula;
import tcc.ges.aprovamais.entity.Orientador;
import tcc.ges.aprovamais.entity.TipoEstagio;
import tcc.ges.aprovamais.entity.Turma;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.entity.enums.StatusMatricula;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.AlunoRepository;
import tcc.ges.aprovamais.repository.EstagioRepository;
import tcc.ges.aprovamais.repository.MatriculaRepository;
import tcc.ges.aprovamais.repository.OrientadorTurmaRepository;
import tcc.ges.aprovamais.repository.TipoEstagioRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    @InjectMocks
    private EstagioService estagioService;
    private Aluno aluno;
    private Orientador orientador;
    private Matricula matricula;
    private TipoEstagio tipoEstagio;

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



        Turma turma = new Turma();
        turma.setId(3L);

        matricula = new Matricula();
        matricula.setId(10L);
        matricula.setAluno(aluno);
        matricula.setTurma(turma);
        matricula.setStatus(StatusMatricula.ATIVA);



        tipoEstagio = new TipoEstagio();
        tipoEstagio.setId(5L);
        tipoEstagio.setNome("Estagiário");
        tipoEstagio.setCargaHorariaNecessaria(new BigDecimal("400"));
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
        estagio.setStatus(status);
        return estagio;
    }

    private EstagioCadastroRequest criarRequest() {
        EstagioCadastroRequest request = new EstagioCadastroRequest();
        request.setTipoEstagioId(tipoEstagio.getId());
        request.setNomeEmpresa("Empresa Nova");
        request.setDataInicio(LocalDate.of(2026, 10, 1));
        return request;
    }





    private void prepararCadastro(Estagio estagioExistente) {
        when(alunoRepository.findByEmail(EMAIL_ALUNO)).thenReturn(Optional.of(aluno));
        when(matriculaRepository.findFirstByAlunoIdAndStatus(aluno.getId(), StatusMatricula.ATIVA)).thenReturn(Optional.of(matricula));
        when(tipoEstagioRepository.findById(tipoEstagio.getId())).thenReturn(Optional.of(tipoEstagio));
        when(estagioRepository.findByMatriculaId(matricula.getId())).thenReturn(Optional.ofNullable(estagioExistente));
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
        EstagioResponse resposta = estagioService.aprovar(ESTAGIO_ID, EMAIL_ORIENTADOR, IP);

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
        ResourceNotFoundException excecao = assertThrows(ResourceNotFoundException.class, () -> estagioService.aprovar(ESTAGIO_ID, EMAIL_ORIENTADOR, IP));

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
}