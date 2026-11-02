package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tcc.ges.aprovamais.dto.PreCadastroRequest;
import tcc.ges.aprovamais.dto.PreCadastroResponse;
import tcc.ges.aprovamais.entity.Aluno;
import tcc.ges.aprovamais.entity.Coordenador;
import tcc.ges.aprovamais.entity.Curso;
import tcc.ges.aprovamais.entity.PreCadastro;
import tcc.ges.aprovamais.entity.Turma;
import tcc.ges.aprovamais.entity.enums.PerfilDestino;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.AlunoRepository;
import tcc.ges.aprovamais.repository.ConviteRepository;
import tcc.ges.aprovamais.repository.CoordenadorRepository;
import tcc.ges.aprovamais.repository.OrientadorRepository;
import tcc.ges.aprovamais.repository.PreCadastroRepository;
import tcc.ges.aprovamais.repository.TurmaRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Testa a lista de pré-cadastro do coordenador
@ExtendWith(MockitoExtension.class)
class PreCadastroServiceTest {

    private static final String EMAIL_COORDENADOR = "coordenador@teste.com";
    private static final String EMAIL = "maria.souza@teste.com";
    private static final String RGM = "11230000001";
    private static final String IP = "127.0.0.1";

    @Mock
    private PreCadastroRepository preCadastroRepository;
    @Mock
    private CoordenadorRepository coordenadorRepository;
    @Mock
    private TurmaRepository turmaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AlunoRepository alunoRepository;
    @Mock
    private OrientadorRepository orientadorRepository;
    @Mock
    private ConviteRepository conviteRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @InjectMocks
    private PreCadastroService preCadastroService;

    private Coordenador coordenador;
    private Turma turma;

    @BeforeEach
    void setUp() {
        coordenador = new Coordenador();
        coordenador.setEmail(EMAIL_COORDENADOR);

        Curso curso = new Curso();
        curso.setNome("Engenharia de Software");
        turma = new Turma();
        turma.setId(5L);
        turma.setCurso(curso);
        turma.setSemestre("2026/2");
        turma.setIdentificador("8B");
    }

    private PreCadastroRequest request(PerfilDestino perfil, Long turmaId) {
        PreCadastroRequest request = new PreCadastroRequest();
        request.setNome("  Maria Souza ");
        request.setEmail("  Maria.Souza@Teste.com ");
        request.setPerfil(perfil);
        request.setIdentificadorInstitucional(RGM);
        request.setTurmaId(turmaId);
        return request;
    }

    @Test
    @DisplayName("Inclui aluno com a turma do próprio curso, com e-mail em minúsculas")
    void deveIncluirAlunoComTurma() {
        when(coordenadorRepository.findByEmail(EMAIL_COORDENADOR)).thenReturn(Optional.of(coordenador));
        when(preCadastroRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
        when(preCadastroRepository.existsByIdentificadorInstitucionalIgnoreCase(RGM)).thenReturn(false);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(alunoRepository.existsByRgm(RGM)).thenReturn(false);
        when(turmaRepository.findByIdAndCursoCoordenadorEmail(5L, EMAIL_COORDENADOR)).thenReturn(Optional.of(turma));
        when(preCadastroRepository.save(any(PreCadastro.class))).thenAnswer(inv -> {
            PreCadastro salvo = inv.getArgument(0);
            salvo.setId(1L);
            return salvo;
        });

        PreCadastroResponse resposta = preCadastroService.cadastrar(EMAIL_COORDENADOR,
                request(PerfilDestino.ALUNO, 5L), IP);

        ArgumentCaptor<PreCadastro> captor = ArgumentCaptor.forClass(PreCadastro.class);
        verify(preCadastroRepository).save(captor.capture());
        PreCadastro salvo = captor.getValue();
        assertEquals(EMAIL, salvo.getEmail());
        assertEquals("Maria Souza", salvo.getNome());
        assertEquals(turma, salvo.getTurma());
        assertEquals(coordenador, salvo.getCoordenador());

        assertEquals("2026/2 - 8B", resposta.getDescricaoTurma());
        assertEquals("Engenharia de Software", resposta.getNomeCurso());
        assertEquals(PreCadastroService.SITUACAO_AGUARDANDO_CONVITE, resposta.getSituacao());
    }

    @Test
    @DisplayName("CA02: e-mail que já está na lista é recusado")
    void deveRecusarEmailDuplicado() {
        when(coordenadorRepository.findByEmail(EMAIL_COORDENADOR)).thenReturn(Optional.of(coordenador));
        when(preCadastroRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> preCadastroService.cadastrar(EMAIL_COORDENADOR, request(PerfilDestino.ALUNO, 5L), IP));

        verify(preCadastroRepository, never()).save(any());
    }

    @Test
    @DisplayName("RN21: turma de curso de outro coordenador responde como inexistente")
    void deveRecusarTurmaDeOutroCoordenador() {
        when(coordenadorRepository.findByEmail(EMAIL_COORDENADOR)).thenReturn(Optional.of(coordenador));
        when(preCadastroRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
        when(preCadastroRepository.existsByIdentificadorInstitucionalIgnoreCase(RGM)).thenReturn(false);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(alunoRepository.existsByRgm(RGM)).thenReturn(false);
        when(turmaRepository.findByIdAndCursoCoordenadorEmail(9L, EMAIL_COORDENADOR)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> preCadastroService.cadastrar(EMAIL_COORDENADOR, request(PerfilDestino.ALUNO, 9L), IP));

        verify(preCadastroRepository, never()).save(any());
    }

    @Test
    @DisplayName("Orientador fica sem turma, mesmo que alguma turma venha na requisição")
    void orientadorFicaSemTurma() {
        when(coordenadorRepository.findByEmail(EMAIL_COORDENADOR)).thenReturn(Optional.of(coordenador));
        when(preCadastroRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
        when(preCadastroRepository.existsByIdentificadorInstitucionalIgnoreCase(RGM)).thenReturn(false);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(orientadorRepository.existsByMatriculaInstitucional(RGM)).thenReturn(false);
        when(preCadastroRepository.save(any(PreCadastro.class))).thenAnswer(inv -> inv.getArgument(0));

        preCadastroService.cadastrar(EMAIL_COORDENADOR, request(PerfilDestino.ORIENTADOR, 5L), IP);

        ArgumentCaptor<PreCadastro> captor = ArgumentCaptor.forClass(PreCadastro.class);
        verify(preCadastroRepository).save(captor.capture());
        assertNull(captor.getValue().getTurma());
        verify(turmaRepository, never()).findByIdAndCursoCoordenadorEmail(anyLong(), anyString());
    }

    @Test
    @DisplayName("E-mail de quem já tem conta ativa é recusado")
    void deveRecusarEmailComConta() {
        Aluno ativo = new Aluno();
        ativo.setEmail(EMAIL);
        ativo.setPrimeiroAcesso(false);
        when(coordenadorRepository.findByEmail(EMAIL_COORDENADOR)).thenReturn(Optional.of(coordenador));
        when(preCadastroRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
        when(preCadastroRepository.existsByIdentificadorInstitucionalIgnoreCase(RGM)).thenReturn(false);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(ativo));

        assertThrows(IllegalStateException.class,
                () -> preCadastroService.cadastrar(EMAIL_COORDENADOR, request(PerfilDestino.ALUNO, 5L), IP));

        verify(preCadastroRepository, never()).save(any());
    }

    @Test
    @DisplayName("Depois do cadastro concluído, o pré-cadastro não pode mais ser alterado")
    void naoAlteraDepoisDoCadastroConcluido() {
        PreCadastro existente = new PreCadastro();
        existente.setId(1L);
        existente.setEmail(EMAIL);
        existente.setPerfil(PerfilDestino.ALUNO);
        existente.setIdentificadorInstitucional(RGM);
        Aluno ativo = new Aluno();
        ativo.setEmail(EMAIL);
        ativo.setPrimeiroAcesso(false);
        when(preCadastroRepository.findByIdAndCoordenadorEmail(1L, EMAIL_COORDENADOR)).thenReturn(Optional.of(existente));
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(ativo));

        assertThrows(IllegalStateException.class,
                () -> preCadastroService.atualizar(1L, EMAIL_COORDENADOR, request(PerfilDestino.ALUNO, 5L), IP));

        verify(preCadastroRepository, never()).save(any());
    }
}
