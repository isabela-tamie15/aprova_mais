package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tcc.ges.aprovamais.dto.CursoRequest;
import tcc.ges.aprovamais.dto.CursoResponse;
import tcc.ges.aprovamais.entity.Coordenador;
import tcc.ges.aprovamais.entity.Curso;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.CoordenadorRepository;
import tcc.ges.aprovamais.repository.CursoRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CursoServiceTest {

    private static final String EMAIL_COORDENADOR = "coordenador@teste.com";
    private static final String IP = "127.0.0.1";

    @Mock
    private CursoRepository cursoRepository;
    @Mock
    private CoordenadorRepository coordenadorRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @InjectMocks
    private CursoService cursoService;

    private Coordenador coordenador;

    @BeforeEach
    void setUp() {
        coordenador = new Coordenador();
        coordenador.setId(1L);
        coordenador.setNome("Coordenador Teste");
        coordenador.setEmail(EMAIL_COORDENADOR);
        coordenador.setPerfil(PerfilUsuario.COORDENADOR);
    }

    private CursoRequest request(String nome, String codigoMec) {
        CursoRequest request = new CursoRequest();
        request.setNome(nome);
        request.setCodigoMec(codigoMec);
        return request;
    }

    @Test
    @DisplayName("Cadastro grava o curso com o coordenador logado como responsável e registra auditoria")
    void deveCadastrarCursoComOCoordenadorLogado() {
        // arrange
        when(coordenadorRepository.findByEmail(EMAIL_COORDENADOR)).thenReturn(Optional.of(coordenador));
        when(cursoRepository.existsByCodigoMec("1105")).thenReturn(false);
        ArgumentCaptor<Curso> captor = ArgumentCaptor.forClass(Curso.class);
        when(cursoRepository.save(captor.capture())).thenAnswer(inv -> {
            Curso salvo = inv.getArgument(0);
            salvo.setId(10L);
            return salvo;
        });

        // act: espaços nas pontas são removidos
        CursoResponse resposta = cursoService.cadastrar(EMAIL_COORDENADOR, request("  Engenharia de Software ", " 1105 "), IP);

        // assert
        Curso salvo = captor.getValue();
        assertEquals("Engenharia de Software", salvo.getNome());
        assertEquals("1105", salvo.getCodigoMec());
        assertEquals(coordenador, salvo.getCoordenador());
        assertEquals(10L, resposta.getId());
        verify(auditoriaService).registrar(eq(coordenador), eq("CURSO_CADASTRADO"), anyString(), eq(IP), eq(true));
    }

    @Test
    @DisplayName("Código e-MEC que já existe impede o cadastro (RN19)")
    void deveRecusarCodigoMecRepetido() {
        // arrange
        when(coordenadorRepository.findByEmail(EMAIL_COORDENADOR)).thenReturn(Optional.of(coordenador));
        when(cursoRepository.existsByCodigoMec("1105")).thenReturn(true);

        // act
        IllegalStateException excecao = assertThrows(IllegalStateException.class,
                () -> cursoService.cadastrar(EMAIL_COORDENADOR, request("Outro curso", "1105"), IP));

        // assert
        assertEquals("Já existe um curso com esse código e-MEC", excecao.getMessage());
        verify(cursoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Curso de outro coordenador é tratado como inexistente na edição (RN21)")
    void deveResponderNaoEncontradoParaCursoDeOutroCoordenador() {
        // arrange: a busca filtrada pelo coordenador não acha o curso
        when(cursoRepository.findByIdAndCoordenadorEmail(5L, EMAIL_COORDENADOR)).thenReturn(Optional.empty());

        // act
        ResourceNotFoundException excecao = assertThrows(ResourceNotFoundException.class,
                () -> cursoService.atualizar(5L, EMAIL_COORDENADOR, request("Novo nome", "9999"), IP));

        // assert
        assertEquals("Curso não encontrado", excecao.getMessage());
        verify(cursoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Na edição, o curso pode manter o próprio código e-MEC")
    void deveAtualizarMantendoOProprioCodigo() {
        // arrange
        Curso curso = new Curso();
        curso.setId(5L);
        curso.setNome("Nome antigo");
        curso.setCodigoMec("1105");
        curso.setCoordenador(coordenador);
        when(cursoRepository.findByIdAndCoordenadorEmail(5L, EMAIL_COORDENADOR)).thenReturn(Optional.of(curso));
        when(cursoRepository.existsByCodigoMecAndIdNot("1105", 5L)).thenReturn(false);
        when(cursoRepository.save(curso)).thenReturn(curso);

        // act
        CursoResponse resposta = cursoService.atualizar(5L, EMAIL_COORDENADOR, request("Nome novo", "1105"), IP);

        // assert
        assertEquals("Nome novo", resposta.getNome());
        verify(auditoriaService).registrar(eq(coordenador), eq("CURSO_ALTERADO"), anyString(), eq(IP), eq(true));
    }
}
