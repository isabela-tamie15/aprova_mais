package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.entity.Aluno;
import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.Matricula;
import tcc.ges.aprovamais.entity.Orientador;
import tcc.ges.aprovamais.entity.enums.MotivoEncerramento;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.external.EmailService;
import tcc.ges.aprovamais.repository.EstagioRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EncerramentoEstagioServiceTest {

    private static final String EMAIL_ORIENTADOR = "orientador@teste.com";
    private static final String EMAIL_COORDENADOR = "coordenador@teste.com";
    private static final String IP = "127.0.0.1";

    @Mock
    private EstagioRepository estagioRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private EstagioService estagioService;
    @Mock
    private CursoService cursoService;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private EmailService emailService;
    @InjectMocks
    private EncerramentoEstagioService encerramentoEstagioService;

    private Estagio estagio;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(encerramentoEstagioService, "appUrl", "http://localhost:8080");

        Aluno aluno = new Aluno();
        aluno.setId(1L);
        aluno.setNome("Aluno Teste");
        aluno.setEmail("aluno@teste.com");

        Matricula matricula = new Matricula();
        matricula.setId(2L);
        matricula.setAluno(aluno);

        estagio = new Estagio();
        estagio.setId(10L);
        estagio.setMatricula(matricula);
        estagio.setOrientador(new Orientador());
        estagio.setStatus(StatusEstagio.ATIVO);
    }

    private void progressoComConclusao(boolean podeConcluir) {
        when(estagioService.montarProgresso(estagio)).thenReturn(EstagioResponse.builder().podeConcluir(podeConcluir).build());
    }

    @Test
    @DisplayName("Conclui o estágio ativo com os critérios cumpridos e avisa o aluno")
    void deveConcluirEstagio() {
        when(estagioRepository.findByIdAndOrientadorEmail(10L, EMAIL_ORIENTADOR)).thenReturn(Optional.of(estagio));
        when(estagioRepository.save(estagio)).thenReturn(estagio);
        when(usuarioRepository.findByEmail(EMAIL_ORIENTADOR)).thenReturn(Optional.of(new Orientador()));
        progressoComConclusao(true);

        encerramentoEstagioService.concluir(10L, EMAIL_ORIENTADOR, IP);

        assertEquals(StatusEstagio.CONCLUIDO, estagio.getStatus());
        assertNull(estagio.getMotivoEncerramento());
        assertEquals(LocalDate.now(), estagio.getDataFinalizacao());
        verify(auditoriaService).registrar(any(), eq("ESTAGIO_CONCLUIDO"), anyString(), eq(IP), eq(true));
        verify(emailService).enviarFinalizacaoEstagio(eq("aluno@teste.com"), eq("Aluno Teste"), eq(true), anyString());
    }

    @Test
    @DisplayName("Recusa a conclusão com carga ou tarefa pendente")
    void naoDeveConcluirComPendencia() {
        when(estagioRepository.findByIdAndOrientadorEmail(10L, EMAIL_ORIENTADOR)).thenReturn(Optional.of(estagio));
        progressoComConclusao(false);

        assertThrows(RegraNegocioException.class, () -> encerramentoEstagioService.concluir(10L, EMAIL_ORIENTADOR, IP));
        assertEquals(StatusEstagio.ATIVO, estagio.getStatus());
        verify(estagioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Recusa a conclusão de um estágio que não está ativo")
    void naoDeveConcluirEstagioPendente() {
        estagio.setStatus(StatusEstagio.PENDENTE);
        when(estagioRepository.findByIdAndOrientadorEmail(10L, EMAIL_ORIENTADOR)).thenReturn(Optional.of(estagio));

        assertThrows(RegraNegocioException.class, () -> encerramentoEstagioService.concluir(10L, EMAIL_ORIENTADOR, IP));
        verify(estagioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Responde 404 para o estágio de outro orientador")
    void naoDeveConcluirEstagioDeOutroOrientador() {
        when(estagioRepository.findByIdAndOrientadorEmail(10L, EMAIL_ORIENTADOR)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> encerramentoEstagioService.concluir(10L, EMAIL_ORIENTADOR, IP));
    }

    @Test
    @DisplayName("Encerra o estágio ativo por reprovação com a observação")
    void deveReprovarEstagio() {
        when(estagioRepository.findByIdAndOrientadorEmail(10L, EMAIL_ORIENTADOR)).thenReturn(Optional.of(estagio));
        when(estagioRepository.save(estagio)).thenReturn(estagio);
        when(usuarioRepository.findByEmail(EMAIL_ORIENTADOR)).thenReturn(Optional.of(new Orientador()));

        encerramentoEstagioService.reprovar(10L, EMAIL_ORIENTADOR, "  Não cumpriu as atividades  ", IP);

        assertEquals(StatusEstagio.ENCERRADO, estagio.getStatus());
        assertEquals(MotivoEncerramento.REPROVACAO, estagio.getMotivoEncerramento());
        assertEquals("Não cumpriu as atividades", estagio.getObservacaoEncerramento());
        verify(auditoriaService).registrar(any(), eq("ESTAGIO_REPROVADO"), anyString(), eq(IP), eq(true));
        verify(emailService).enviarFinalizacaoEstagio(anyString(), anyString(), eq(false), anyString());
    }

    @Test
    @DisplayName("Encerra por desligamento um estágio pendente do curso do coordenador")
    void deveDesligarEstagio() {
        estagio.setStatus(StatusEstagio.PENDENTE);
        when(estagioRepository.findByIdAndMatriculaTurmaCursoCoordenadorEmail(10L, EMAIL_COORDENADOR))
                .thenReturn(Optional.of(estagio));
        when(estagioRepository.save(estagio)).thenReturn(estagio);
        when(usuarioRepository.findByEmail(EMAIL_COORDENADOR)).thenReturn(Optional.of(new Orientador()));

        encerramentoEstagioService.desligar(10L, EMAIL_COORDENADOR, "Saiu da empresa", IP);

        assertEquals(StatusEstagio.ENCERRADO, estagio.getStatus());
        assertEquals(MotivoEncerramento.DESLIGAMENTO, estagio.getMotivoEncerramento());
        assertNotNull(estagio.getDataFinalizacao());
        verify(auditoriaService).registrar(any(), eq("ESTAGIO_DESLIGADO"), anyString(), eq(IP), eq(true));
    }

    @Test
    @DisplayName("Um estágio encerrado não muda mais de status")
    void naoDeveDesligarEstagioEncerrado() {
        estagio.setStatus(StatusEstagio.ENCERRADO);
        estagio.setMotivoEncerramento(MotivoEncerramento.REPROVACAO);
        when(estagioRepository.findByIdAndMatriculaTurmaCursoCoordenadorEmail(10L, EMAIL_COORDENADOR))
                .thenReturn(Optional.of(estagio));

        assertThrows(RegraNegocioException.class,
                () -> encerramentoEstagioService.desligar(10L, EMAIL_COORDENADOR, "Saiu da empresa", IP));
        assertEquals(MotivoEncerramento.REPROVACAO, estagio.getMotivoEncerramento());
        verify(emailService, never()).enviarFinalizacaoEstagio(anyString(), anyString(), anyBoolean(), anyString());
    }

    @Test
    @DisplayName("Responde 404 para o estágio de um curso de outro coordenador")
    void naoDeveDesligarEstagioDeOutroCurso() {
        when(estagioRepository.findByIdAndMatriculaTurmaCursoCoordenadorEmail(10L, EMAIL_COORDENADOR))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> encerramentoEstagioService.desligar(10L, EMAIL_COORDENADOR, "Saiu da empresa", IP));
    }
}
