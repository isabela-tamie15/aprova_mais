package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import tcc.ges.aprovamais.entity.Aluno;
import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.Secretaria;
import tcc.ges.aprovamais.entity.enums.MotivoEncerramento;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.exception.DadoInvalidoException;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.repository.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnonimizacaoServiceTest {

    private static final String EMAIL = "maria.souza@teste.com";
    private static final String IP = "127.0.0.1";

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AlunoRepository alunoRepository;
    @Mock
    private OrientadorRepository orientadorRepository;
    @Mock
    private SecretariaRepository secretariaRepository;
    @Mock
    private CursoRepository cursoRepository;
    @Mock
    private PreCadastroRepository preCadastroRepository;
    @Mock
    private ConviteRepository conviteRepository;
    @Mock
    private LogAuditoriaRepository logAuditoriaRepository;
    @Mock
    private EstagioRepository estagioRepository;
    @Mock
    private OrientadorTurmaRepository orientadorTurmaRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private DoisFatoresService doisFatoresService;
    @Mock
    private BloqueioContaService bloqueioContaService;
    @InjectMocks
    private AnonimizacaoService anonimizacaoService;

    private Aluno aluno;

    @BeforeEach
    void setUp() {
        aluno = new Aluno();
        aluno.setId(7L);
        aluno.setNome("Maria Souza");
        aluno.setEmail(EMAIL);
        aluno.setSenhaHash("hash");
        aluno.setPerfil(PerfilUsuario.ALUNO);
        aluno.setRgm("11232100617");
        aluno.setDoisFatoresAtivo(false);
    }

    @Test
    @DisplayName("Senha errada recusa a anonimização e não altera nenhum dado")
    void deveRecusarSenhaErrada() {
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(aluno));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        DadoInvalidoException excecao = assertThrows(DadoInvalidoException.class,
                () -> anonimizacaoService.anonimizar(EMAIL, "errada", null, IP));

        assertEquals("Senha incorreta.", excecao.getMessage());
        assertEquals(EMAIL, aluno.getEmail());
        verify(bloqueioContaService).registrarFalha(aluno);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Aluno anonimizado: dados trocados, sessões encerradas, estágio em aberto encerrado e e-mail trocado na auditoria")
    void deveAnonimizarAlunoPorCompleto() {
        Estagio estagioAtivo = new Estagio();
        estagioAtivo.setStatus(StatusEstagio.ATIVO);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(aluno));
        when(passwordEncoder.matches("Senha123", "hash")).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("hash-aleatorio");
        when(alunoRepository.findById(7L)).thenReturn(Optional.of(aluno));
        when(estagioRepository.findByMatriculaAlunoIdAndStatusIn(7L, EstagioService.STATUS_EM_ABERTO))
                .thenReturn(List.of(estagioAtivo));
        when(preCadastroRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

        anonimizacaoService.anonimizar(EMAIL, "Senha123", null, IP);

        assertEquals("USUÁRIO REMOVIDO", aluno.getNome());
        assertTrue(aluno.getEmail().startsWith("removido_"));
        assertTrue(aluno.getRgm().startsWith("REMOVIDO_"));
        assertFalse(aluno.getAtivo());
        assertEquals(1, aluno.versaoSessaoAtual());
        assertEquals(StatusEstagio.ENCERRADO, estagioAtivo.getStatus());
        assertEquals(MotivoEncerramento.DESLIGAMENTO, estagioAtivo.getMotivoEncerramento());
        verify(conviteRepository).trocarEmail(eq(EMAIL), anyString());
        verify(logAuditoriaRepository).trocarEmail(eq(EMAIL), anyString());
        verify(logAuditoriaRepository).trocarTextoNosDetalhes(eq("Maria Souza"), eq("USUÁRIO REMOVIDO"));
    }

    @Test
    @DisplayName("A última secretaria ativa não pode se anonimizar")
    void deveRecusarUltimaSecretaria() {
        Secretaria secretaria = new Secretaria();
        secretaria.setId(3L);
        secretaria.setEmail(EMAIL);
        secretaria.setPerfil(PerfilUsuario.SECRETARIA);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(secretaria));
        when(secretariaRepository.countByAtivoTrue()).thenReturn(1L);

        assertThrows(RegraNegocioException.class,
                () -> anonimizacaoService.anonimizar(EMAIL, "Senha123", null, IP));
        verify(usuarioRepository, never()).save(any());
    }
}
