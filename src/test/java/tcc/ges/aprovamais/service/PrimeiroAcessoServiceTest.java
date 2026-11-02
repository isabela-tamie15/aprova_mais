package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import tcc.ges.aprovamais.entity.Aluno;
import tcc.ges.aprovamais.entity.Convite;
import tcc.ges.aprovamais.entity.Matricula;
import tcc.ges.aprovamais.entity.Orientador;
import tcc.ges.aprovamais.entity.PreCadastro;
import tcc.ges.aprovamais.entity.Turma;
import tcc.ges.aprovamais.entity.enums.PerfilDestino;
import tcc.ges.aprovamais.entity.enums.StatusMatricula;
import tcc.ges.aprovamais.repository.MatriculaRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Testa o aceite do convite com os dados do pré-cadastro e a matrícula criada no aceite
@ExtendWith(MockitoExtension.class)
class PrimeiroAcessoServiceTest {

    private static final String TOKEN = "token-do-convite";
    private static final String EMAIL = "maria.souza@teste.com";
    private static final String SENHA = "senhaForte1";
    private static final String IP = "127.0.0.1";

    @Mock
    private ConviteService conviteService;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private ConsentimentoService consentimentoService;
    @Mock
    private PreCadastroService preCadastroService;
    @Mock
    private MatriculaRepository matriculaRepository;
    @InjectMocks
    private PrimeiroAcessoService primeiroAcessoService;

    private Turma turma;

    @BeforeEach
    void setUp() {
        turma = new Turma();
        turma.setId(5L);
    }

    private Convite convite(PerfilDestino perfil) {
        Convite convite = new Convite();
        convite.setEmail(EMAIL);
        convite.setPerfilDestino(perfil);
        return convite;
    }

    private PreCadastro preCadastro(PerfilDestino perfil, String identificador) {
        PreCadastro preCadastro = new PreCadastro();
        preCadastro.setEmail(EMAIL);
        preCadastro.setNome("Maria Souza");
        preCadastro.setPerfil(perfil);
        preCadastro.setIdentificadorInstitucional(identificador);
        preCadastro.setTurma(perfil == PerfilDestino.ALUNO ? turma : null);
        return preCadastro;
    }

    private Aluno alunoPendente() {
        Aluno aluno = new Aluno();
        aluno.setId(7L);
        aluno.setEmail(EMAIL);
        aluno.setNome("Maria Souza");
        aluno.setRgm("PENDENTE_" + EMAIL);
        aluno.setPrimeiroAcesso(true);
        aluno.setAtivo(false);
        return aluno;
    }

    @Test
    @DisplayName("CA01: aluno aceita o convite, a conta fica ativa e a matrícula é criada na turma do pré-cadastro")
    void deveAtivarAlunoECriarMatricula() {
        Aluno aluno = alunoPendente();
        when(conviteService.validarToken(TOKEN)).thenReturn(convite(PerfilDestino.ALUNO));
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(aluno));
        when(passwordEncoder.encode(SENHA)).thenReturn("hash");
        when(preCadastroService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(preCadastro(PerfilDestino.ALUNO, "11230000001")));
        when(matriculaRepository.findByAlunoIdAndTurmaId(7L, 5L)).thenReturn(Optional.empty());
        when(matriculaRepository.save(any(Matricula.class))).thenAnswer(inv -> inv.getArgument(0));

        primeiroAcessoService.concluirCadastro(TOKEN, SENHA, IP);

        assertTrue(aluno.getAtivo());
        assertFalse(aluno.getPrimeiroAcesso());
        assertEquals("11230000001", aluno.getRgm());

        ArgumentCaptor<Matricula> captor = ArgumentCaptor.forClass(Matricula.class);
        verify(matriculaRepository).save(captor.capture());
        assertEquals(aluno, captor.getValue().getAluno());
        assertEquals(turma, captor.getValue().getTurma());
        assertEquals(StatusMatricula.ATIVA, captor.getValue().getStatus());
        verify(conviteService).aceitarConvite(TOKEN);
    }

    @Test
    @DisplayName("RN28: matrícula que já existe na turma não é duplicada")
    void naoDuplicaMatricula() {
        when(conviteService.validarToken(TOKEN)).thenReturn(convite(PerfilDestino.ALUNO));
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(alunoPendente()));
        when(passwordEncoder.encode(SENHA)).thenReturn("hash");
        when(preCadastroService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(preCadastro(PerfilDestino.ALUNO, "11230000001")));
        when(matriculaRepository.findByAlunoIdAndTurmaId(7L, 5L)).thenReturn(Optional.of(new Matricula()));

        primeiroAcessoService.concluirCadastro(TOKEN, SENHA, IP);

        verify(matriculaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Orientador recebe a matrícula institucional do pré-cadastro e não ganha matrícula em turma")
    void orientadorRecebeMatriculaInstitucional() {
        Orientador orientador = new Orientador();
        orientador.setEmail(EMAIL);
        orientador.setMatriculaInstitucional("PENDENTE_" + EMAIL);
        orientador.setPrimeiroAcesso(true);
        when(conviteService.validarToken(TOKEN)).thenReturn(convite(PerfilDestino.ORIENTADOR));
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(orientador));
        when(passwordEncoder.encode(SENHA)).thenReturn("hash");
        when(preCadastroService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(preCadastro(PerfilDestino.ORIENTADOR, "ORI-123")));

        primeiroAcessoService.concluirCadastro(TOKEN, SENHA, IP);

        assertEquals("ORI-123", orientador.getMatriculaInstitucional());
        assertEquals("Maria Souza", orientador.getNome());
        verify(matriculaRepository, never()).save(any());
    }
}
