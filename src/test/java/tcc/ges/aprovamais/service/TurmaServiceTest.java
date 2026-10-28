package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tcc.ges.aprovamais.dto.TurmaRequest;
import tcc.ges.aprovamais.dto.TurmaResponse;
import tcc.ges.aprovamais.entity.Coordenador;
import tcc.ges.aprovamais.entity.Curso;
import tcc.ges.aprovamais.entity.Turma;
import tcc.ges.aprovamais.entity.enums.Modalidade;
import tcc.ges.aprovamais.entity.enums.Periodo;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.TurmaRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TurmaServiceTest {

    private static final String EMAIL_COORDENADOR = "coordenador@teste.com";
    private static final String IP = "127.0.0.1";

    @Mock
    private TurmaRepository turmaRepository;
    @Mock
    private CursoService cursoService;
    @Mock
    private AuditoriaService auditoriaService;
    @InjectMocks
    private TurmaService turmaService;

    private Coordenador coordenador;
    private Curso curso;

    @BeforeEach
    void setUp() {
        coordenador = new Coordenador();
        coordenador.setId(1L);
        coordenador.setEmail(EMAIL_COORDENADOR);

        curso = new Curso();
        curso.setId(3L);
        curso.setNome("Engenharia de Software");
        curso.setCoordenador(coordenador);
    }

    private TurmaRequest request(Long cursoId, String identificador) {
        TurmaRequest request = new TurmaRequest();
        request.setCursoId(cursoId);
        request.setSemestre("2026/2");
        request.setIdentificador(identificador);
        request.setPeriodo(Periodo.NOTURNO);
        request.setModalidade(Modalidade.PRESENCIAL);
        return request;
    }

    @Test
    @DisplayName("Cadastro grava a turma no curso do coordenador, com o identificador em maiúsculas")
    void deveCadastrarTurmaNoCursoDoCoordenador() {
        // arrange
        when(cursoService.buscarCursoDoCoordenador(3L, EMAIL_COORDENADOR)).thenReturn(curso);
        when(turmaRepository.existsByCursoIdAndSemestreAndIdentificadorIgnoreCase(3L, "2026/2", "8B")).thenReturn(false);
        ArgumentCaptor<Turma> captor = ArgumentCaptor.forClass(Turma.class);
        when(turmaRepository.save(captor.capture())).thenAnswer(inv -> {
            Turma salva = inv.getArgument(0);
            salva.setId(20L);
            return salva;
        });

        // act
        TurmaResponse resposta = turmaService.cadastrar(EMAIL_COORDENADOR, request(3L, " 8b "), IP);

        // assert
        assertEquals("8B", captor.getValue().getIdentificador());
        assertEquals(curso, captor.getValue().getCurso());
        assertEquals("NOTURNO", resposta.getPeriodo());
        verify(auditoriaService).registrar(eq(coordenador), eq("TURMA_CADASTRADA"), anyString(), eq(IP), eq(true));
    }

    @Test
    @DisplayName("Curso de outro coordenador impede o cadastro da turma (RN21)")
    void deveRecusarTurmaEmCursoDeOutroCoordenador() {
        // arrange: o CursoService responde 404 quando o curso não é do coordenador
        when(cursoService.buscarCursoDoCoordenador(9L, EMAIL_COORDENADOR))
                .thenThrow(new ResourceNotFoundException("Curso não encontrado"));

        // act + assert
        assertThrows(ResourceNotFoundException.class,
                () -> turmaService.cadastrar(EMAIL_COORDENADOR, request(9L, "8B"), IP));
        verify(turmaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Mesma turma no mesmo semestre do curso é recusada")
    void deveRecusarTurmaRepetida() {
        // arrange
        when(cursoService.buscarCursoDoCoordenador(3L, EMAIL_COORDENADOR)).thenReturn(curso);
        when(turmaRepository.existsByCursoIdAndSemestreAndIdentificadorIgnoreCase(3L, "2026/2", "8B")).thenReturn(true);

        // act
        IllegalStateException excecao = assertThrows(IllegalStateException.class,
                () -> turmaService.cadastrar(EMAIL_COORDENADOR, request(3L, "8B"), IP));

        // assert
        assertEquals("Já existe a turma 8B neste semestre do curso", excecao.getMessage());
        verify(turmaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Turma não pode mudar de curso na edição")
    void deveRecusarMudancaDeCurso() {
        // arrange
        Turma turma = new Turma();
        turma.setId(20L);
        turma.setCurso(curso);
        when(turmaRepository.findByIdAndCursoCoordenadorEmail(20L, EMAIL_COORDENADOR)).thenReturn(Optional.of(turma));

        // act
        IllegalStateException excecao = assertThrows(IllegalStateException.class,
                () -> turmaService.atualizar(20L, EMAIL_COORDENADOR, request(4L, "8B"), IP));

        // assert
        assertEquals("Uma turma não pode mudar de curso", excecao.getMessage());
        verify(turmaRepository, never()).save(any());
    }
}
