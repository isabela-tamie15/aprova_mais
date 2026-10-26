package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tcc.ges.aprovamais.dto.TipoEstagioRequest;
import tcc.ges.aprovamais.dto.TipoEstagioResponse;
import tcc.ges.aprovamais.entity.Coordenador;
import tcc.ges.aprovamais.entity.Curso;
import tcc.ges.aprovamais.entity.TipoEstagio;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.TipoEstagioRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TipoEstagioServiceTest {

    private static final String EMAIL_COORDENADOR = "coordenador@teste.com";
    private static final String IP = "127.0.0.1";

    @Mock
    private TipoEstagioRepository tipoEstagioRepository;
    @Mock
    private CursoService cursoService;
    @Mock
    private AuditoriaService auditoriaService;
    @InjectMocks
    private TipoEstagioService tipoEstagioService;

    private Coordenador coordenador;
    private Curso curso;

    @BeforeEach
    void setUp() {
        coordenador = new Coordenador();
        coordenador.setId(1L);
        coordenador.setEmail(EMAIL_COORDENADOR);

        curso = new Curso();
        curso.setId(3L);
        curso.setCoordenador(coordenador);
    }

    private TipoEstagioRequest request(String nome, String carga, String jornada) {
        TipoEstagioRequest request = new TipoEstagioRequest();
        request.setCursoId(3L);
        request.setNome(nome);
        request.setCargaHorariaNecessaria(new BigDecimal(carga));
        request.setJornadaSemanal(new BigDecimal(jornada));
        return request;
    }

    @Test
    @DisplayName("Cadastro grava o tipo no curso com a carga total e a jornada semanal")
    void deveCadastrarTipoComCargaEJornada() {
        // arrange
        when(cursoService.buscarCursoDoCoordenador(3L, EMAIL_COORDENADOR)).thenReturn(curso);
        when(tipoEstagioRepository.existsByCursoIdAndNomeIgnoreCase(3L, "Estagiário")).thenReturn(false);
        ArgumentCaptor<TipoEstagio> captor = ArgumentCaptor.forClass(TipoEstagio.class);
        when(tipoEstagioRepository.save(captor.capture())).thenAnswer(inv -> {
            TipoEstagio salvo = inv.getArgument(0);
            salvo.setId(7L);
            return salvo;
        });

        // act
        TipoEstagioResponse resposta = tipoEstagioService.cadastrar(
                EMAIL_COORDENADOR, request(" Estagiário ", "270", "30"), IP);

        // assert: descrição em branco vira nula
        TipoEstagio salvo = captor.getValue();
        assertEquals("Estagiário", salvo.getNome());
        assertEquals(curso, salvo.getCurso());
        assertNull(salvo.getDescricao());
        assertEquals(new BigDecimal("270"), resposta.getCargaHorariaNecessaria());
        assertEquals(new BigDecimal("30"), resposta.getJornadaSemanal());
        verify(auditoriaService).registrar(eq(coordenador), eq("TIPO_ESTAGIO_CADASTRADO"), anyString(), eq(IP), eq(true));
    }

    @Test
    @DisplayName("Dois tipos com o mesmo nome no mesmo curso são recusados")
    void deveRecusarTipoRepetidoNoCurso() {
        // arrange
        when(cursoService.buscarCursoDoCoordenador(3L, EMAIL_COORDENADOR)).thenReturn(curso);
        when(tipoEstagioRepository.existsByCursoIdAndNomeIgnoreCase(3L, "CLT")).thenReturn(true);

        // act
        IllegalStateException excecao = assertThrows(IllegalStateException.class,
                () -> tipoEstagioService.cadastrar(EMAIL_COORDENADOR, request("CLT", "270", "44"), IP));

        // assert
        assertEquals("Já existe o tipo de estágio CLT neste curso", excecao.getMessage());
        verify(tipoEstagioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tipo de curso de outro coordenador é tratado como inexistente na edição")
    void deveResponderNaoEncontradoParaTipoDeOutroCoordenador() {
        // arrange
        when(tipoEstagioRepository.findByIdAndCursoCoordenadorEmail(7L, EMAIL_COORDENADOR)).thenReturn(Optional.empty());

        // act + assert
        assertThrows(ResourceNotFoundException.class,
                () -> tipoEstagioService.atualizar(7L, EMAIL_COORDENADOR, request("CLT", "270", "44"), IP));
        verify(tipoEstagioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Edição troca a carga e a jornada do tipo (os estágios já cadastrados guardam a cópia antiga)")
    void deveAtualizarCargaEJornada() {
        // arrange
        TipoEstagio tipo = new TipoEstagio();
        tipo.setId(7L);
        tipo.setCurso(curso);
        tipo.setNome("Estagiário");
        tipo.setCargaHorariaNecessaria(new BigDecimal("270"));
        tipo.setJornadaSemanal(new BigDecimal("30"));
        when(tipoEstagioRepository.findByIdAndCursoCoordenadorEmail(7L, EMAIL_COORDENADOR)).thenReturn(Optional.of(tipo));
        when(tipoEstagioRepository.existsByCursoIdAndNomeIgnoreCaseAndIdNot(3L, "Estagiário", 7L)).thenReturn(false);
        when(tipoEstagioRepository.save(tipo)).thenReturn(tipo);

        // act
        TipoEstagioResponse resposta = tipoEstagioService.atualizar(
                7L, EMAIL_COORDENADOR, request("Estagiário", "300", "20"), IP);

        // assert
        assertEquals(new BigDecimal("300"), resposta.getCargaHorariaNecessaria());
        assertEquals(new BigDecimal("20"), resposta.getJornadaSemanal());
    }
}
