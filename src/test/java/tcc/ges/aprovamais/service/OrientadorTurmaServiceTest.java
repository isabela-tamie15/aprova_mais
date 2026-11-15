package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tcc.ges.aprovamais.dto.TurmaResponse;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.Modalidade;
import tcc.ges.aprovamais.entity.enums.Periodo;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.EstagioRepository;
import tcc.ges.aprovamais.repository.OrientadorRepository;
import tcc.ges.aprovamais.repository.OrientadorTurmaRepository;
import tcc.ges.aprovamais.repository.TurmaRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrientadorTurmaServiceTest {

    private static final String EMAIL_COORDENADOR = "coordenador@teste.com";
    private static final String IP = "127.0.0.1";

    @Mock
    private TurmaRepository turmaRepository;
    @Mock
    private OrientadorRepository orientadorRepository;
    @Mock
    private OrientadorTurmaRepository orientadorTurmaRepository;
    @Mock
    private EstagioRepository estagioRepository;
    @Mock
    private TurmaService turmaService;
    @Mock
    private AuditoriaService auditoriaService;
    @InjectMocks
    private OrientadorTurmaService orientadorTurmaService;

    private Turma turma;
    private Orientador orientadorNovo;
    private Orientador orientadorAntigo;

    @BeforeEach
    void setUp() {
        Coordenador coordenador = new Coordenador();
        coordenador.setId(1L);
        coordenador.setEmail(EMAIL_COORDENADOR);

        Curso curso = new Curso();
        curso.setId(3L);
        curso.setCoordenador(coordenador);

        turma = new Turma();
        turma.setId(10L);
        turma.setCurso(curso);
        turma.setIdentificador("8B");
        turma.setPeriodo(Periodo.NOTURNO);
        turma.setModalidade(Modalidade.PRESENCIAL);

        orientadorNovo = new Orientador();
        orientadorNovo.setId(20L);
        orientadorNovo.setNome("Orientador Novo");

        orientadorAntigo = new Orientador();
        orientadorAntigo.setId(21L);
        orientadorAntigo.setNome("Orientador Antigo");
    }

    private Estagio estagio(Long id, Orientador orientador) {
        Estagio estagio = new Estagio();
        estagio.setId(id);
        estagio.setOrientador(orientador);
        estagio.setStatus(StatusEstagio.PENDENTE);
        return estagio;
    }

    @Test
    @DisplayName("Primeira atribuição cria o vínculo e passa os estágios em aberto da turma para o orientador")
    void deveCriarVinculoETransferirEstagiosEmAberto() {
        // arrange
        Estagio semOrientador = estagio(1L, null);
        Estagio comOutroOrientador = estagio(2L, orientadorAntigo);
        Estagio jaDoNovo = estagio(3L, orientadorNovo);
        when(turmaRepository.findByIdAndCursoCoordenadorEmail(10L, EMAIL_COORDENADOR)).thenReturn(Optional.of(turma));
        when(orientadorRepository.findByIdAndAtivoTrue(20L)).thenReturn(Optional.of(orientadorNovo));
        when(orientadorTurmaRepository.findByTurmaId(10L)).thenReturn(Optional.empty());
        when(estagioRepository.findByMatriculaTurmaIdAndStatusIn(10L, EstagioService.STATUS_EM_ABERTO))
                .thenReturn(List.of(semOrientador, comOutroOrientador, jaDoNovo));
        when(turmaService.paraResponse(turma, orientadorNovo)).thenReturn(TurmaResponse.builder()
                .id(10L).orientadorId(20L).nomeOrientador("Orientador Novo").build());
        ArgumentCaptor<OrientadorTurma> captor = ArgumentCaptor.forClass(OrientadorTurma.class);

        // act
        TurmaResponse resposta = orientadorTurmaService.atribuirOrientador(10L, 20L, EMAIL_COORDENADOR, IP);

        // assert
        verify(orientadorTurmaRepository).save(captor.capture());
        assertSame(turma, captor.getValue().getTurma());
        assertSame(orientadorNovo, captor.getValue().getOrientador());
        assertSame(orientadorNovo, semOrientador.getOrientador());
        assertSame(orientadorNovo, comOutroOrientador.getOrientador());
        // O estágio que já era do orientador não é salvo de novo
        verify(estagioRepository, times(2)).save(any(Estagio.class));
        verify(auditoriaService).registrar(eq(turma.getCurso().getCoordenador()), eq("ORIENTADOR_ATRIBUIDO"),
                anyString(), eq(IP), eq(true));
        assertEquals(20L, resposta.getOrientadorId());
    }

    @Test
    @DisplayName("Troca de orientador altera o vínculo que já existe, sem criar um segundo")
    void deveTrocarOrientadorNoMesmoVinculo() {
        // arrange
        OrientadorTurma vinculo = new OrientadorTurma();
        vinculo.setId(5L);
        vinculo.setTurma(turma);
        vinculo.setOrientador(orientadorAntigo);
        when(turmaRepository.findByIdAndCursoCoordenadorEmail(10L, EMAIL_COORDENADOR)).thenReturn(Optional.of(turma));
        when(orientadorRepository.findByIdAndAtivoTrue(20L)).thenReturn(Optional.of(orientadorNovo));
        when(orientadorTurmaRepository.findByTurmaId(10L)).thenReturn(Optional.of(vinculo));
        when(estagioRepository.findByMatriculaTurmaIdAndStatusIn(10L, EstagioService.STATUS_EM_ABERTO)).thenReturn(List.of());

        // act
        orientadorTurmaService.atribuirOrientador(10L, 20L, EMAIL_COORDENADOR, IP);

        // assert
        verify(orientadorTurmaRepository).save(vinculo);
        assertEquals(5L, vinculo.getId());
        assertSame(orientadorNovo, vinculo.getOrientador());
    }

    @Test
    @DisplayName("Turma de curso de outro coordenador responde 404 e não grava nada")
    void deveRecusarTurmaDeOutroCoordenador() {
        // arrange
        when(turmaRepository.findByIdAndCursoCoordenadorEmail(10L, EMAIL_COORDENADOR)).thenReturn(Optional.empty());

        // act + assert
        assertThrows(ResourceNotFoundException.class,
                () -> orientadorTurmaService.atribuirOrientador(10L, 20L, EMAIL_COORDENADOR, IP));
        verify(orientadorTurmaRepository, never()).save(any());
        verify(estagioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Orientador sem conta ativa não recebe turma")
    void deveRecusarOrientadorSemContaAtiva() {
        // arrange
        when(turmaRepository.findByIdAndCursoCoordenadorEmail(10L, EMAIL_COORDENADOR)).thenReturn(Optional.of(turma));
        when(orientadorRepository.findByIdAndAtivoTrue(20L)).thenReturn(Optional.empty());

        // act + assert
        assertThrows(ResourceNotFoundException.class,
                () -> orientadorTurmaService.atribuirOrientador(10L, 20L, EMAIL_COORDENADOR, IP));
        verify(orientadorTurmaRepository, never()).save(any());
    }
}
