package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tcc.ges.aprovamais.dto.TarefaRequest;
import tcc.ges.aprovamais.dto.TrilhaRequest;
import tcc.ges.aprovamais.entity.Coordenador;
import tcc.ges.aprovamais.entity.Curso;
import tcc.ges.aprovamais.entity.Tarefa;
import tcc.ges.aprovamais.entity.TipoEstagio;
import tcc.ges.aprovamais.entity.Trilha;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.TarefaRepository;
import tcc.ges.aprovamais.repository.TipoEstagioRepository;
import tcc.ges.aprovamais.repository.TrilhaRepository;

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
class TrilhaCoordenadorServiceTest {

    private static final String EMAIL_COORDENADOR = "coordenador@teste.com";
    private static final String IP = "127.0.0.1";

    @Mock
    private TrilhaRepository trilhaRepository;
    @Mock
    private TarefaRepository tarefaRepository;
    @Mock
    private TipoEstagioRepository tipoEstagioRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @InjectMocks
    private TrilhaCoordenadorService trilhaCoordenadorService;

    private Coordenador coordenador;
    private TipoEstagio tipo;
    private Trilha trilha;

    @BeforeEach
    void setUp() {
        coordenador = new Coordenador();
        coordenador.setEmail(EMAIL_COORDENADOR);

        Curso curso = new Curso();
        curso.setId(3L);
        curso.setCoordenador(coordenador);

        tipo = new TipoEstagio();
        tipo.setId(5L);
        tipo.setNome("Estagiário");
        tipo.setCurso(curso);

        trilha = new Trilha();
        trilha.setId(9L);
        trilha.setNome("Trilha Estagiário");
        trilha.setTipoEstagio(tipo);
    }

    private TrilhaRequest trilhaRequest() {
        TrilhaRequest request = new TrilhaRequest();
        request.setTipoEstagioId(5L);
        request.setNome("Trilha Estagiário");
        return request;
    }

    private TarefaRequest tarefaRequest(int ordem, String link) {
        TarefaRequest request = new TarefaRequest();
        request.setNome("Termo de Compromisso");
        request.setOrdem(ordem);
        request.setLinkApoio(link);
        return request;
    }

    @Test
    @DisplayName("Tipo que já tem trilha não ganha outra (CA01 do RF15, RN32)")
    void deveRecusarSegundaTrilhaNoTipo() {
        // arrange
        when(tipoEstagioRepository.findByIdAndCursoCoordenadorEmail(5L, EMAIL_COORDENADOR)).thenReturn(Optional.of(tipo));
        when(trilhaRepository.existsByTipoEstagioId(5L)).thenReturn(true);

        // act
        IllegalStateException excecao = assertThrows(IllegalStateException.class,
                () -> trilhaCoordenadorService.criar(EMAIL_COORDENADOR, trilhaRequest(), IP));

        // assert
        assertEquals("Este tipo de estágio já tem uma trilha", excecao.getMessage());
        verify(trilhaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tipo de curso de outro coordenador é tratado como inexistente")
    void deveResponderNaoEncontradoParaTipoDeOutroCoordenador() {
        // arrange
        when(tipoEstagioRepository.findByIdAndCursoCoordenadorEmail(5L, EMAIL_COORDENADOR)).thenReturn(Optional.empty());

        // act + assert
        assertThrows(ResourceNotFoundException.class,
                () -> trilhaCoordenadorService.criar(EMAIL_COORDENADOR, trilhaRequest(), IP));
        verify(trilhaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tarefa nova entra na trilha com o link de apoio, e link em branco vira nulo")
    void deveAdicionarTarefaComLinkDeApoio() {
        // arrange
        when(trilhaRepository.findByIdAndTipoEstagioCursoCoordenadorEmail(9L, EMAIL_COORDENADOR)).thenReturn(Optional.of(trilha));
        when(tarefaRepository.existsByTrilhaIdAndOrdem(9L, 1)).thenReturn(false);
        ArgumentCaptor<Tarefa> captor = ArgumentCaptor.forClass(Tarefa.class);
        when(tarefaRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(tarefaRepository.findByTrilhaIdOrderByOrdem(9L)).thenReturn(List.of());

        // act
        trilhaCoordenadorService.adicionarTarefa(9L, EMAIL_COORDENADOR,
                tarefaRequest(1, " https://umc.br/modelo.pdf "), IP);

        // assert
        assertEquals("https://umc.br/modelo.pdf", captor.getValue().getLinkApoio());
        assertEquals(trilha, captor.getValue().getTrilha());
        verify(auditoriaService).registrar(eq(coordenador), eq("TAREFA_ADICIONADA"), anyString(), eq(IP), eq(true));
    }

    @Test
    @DisplayName("Duas tarefas na mesma posição da trilha são recusadas")
    void deveRecusarTarefaNaMesmaPosicao() {
        // arrange
        when(trilhaRepository.findByIdAndTipoEstagioCursoCoordenadorEmail(9L, EMAIL_COORDENADOR)).thenReturn(Optional.of(trilha));
        when(tarefaRepository.existsByTrilhaIdAndOrdem(9L, 1)).thenReturn(true);

        // act
        IllegalStateException excecao = assertThrows(IllegalStateException.class,
                () -> trilhaCoordenadorService.adicionarTarefa(9L, EMAIL_COORDENADOR, tarefaRequest(1, ""), IP));

        // assert
        assertEquals("Já existe uma tarefa na posição 1 desta trilha", excecao.getMessage());
        verify(tarefaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tarefa de trilha de outro coordenador não pode ser removida")
    void naoDeveRemoverTarefaDeOutroCoordenador() {
        // arrange
        when(tarefaRepository.findByIdAndTrilhaTipoEstagioCursoCoordenadorEmail(11L, EMAIL_COORDENADOR))
                .thenReturn(Optional.empty());

        // act + assert
        assertThrows(ResourceNotFoundException.class,
                () -> trilhaCoordenadorService.removerTarefa(11L, EMAIL_COORDENADOR, IP));
        verify(tarefaRepository, never()).delete(any());
    }
}
