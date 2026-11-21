package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.MotivoEncerramento;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.exception.DadoInvalidoException;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.repository.EstagioRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrecaoExcepcionalServiceTest {

    private static final String EMAIL_ADMIN = "admin@teste.com";
    private static final String EMAIL_ORIENTADOR = "orientador@teste.com";
    private static final String IP = "127.0.0.1";

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private EstagioRepository estagioRepository;
    @Mock
    private EstagioService estagioService;
    @Mock
    private AuditoriaService auditoriaService;
    @InjectMocks
    private CorrecaoExcepcionalService correcaoExcepcionalService;

    private Administrador administrador;
    private Estagio estagio;

    @BeforeEach
    void setUp() {
        administrador = new Administrador();
        administrador.setId(1L);
        administrador.setEmail(EMAIL_ADMIN);

        Matricula matricula = new Matricula();
        matricula.setId(2L);

        estagio = new Estagio();
        estagio.setId(10L);
        estagio.setMatricula(matricula);
        estagio.setStatus(StatusEstagio.ENCERRADO);
        estagio.setMotivoEncerramento(MotivoEncerramento.DESLIGAMENTO);
        estagio.setDataFinalizacao(LocalDate.of(2026, 11, 1));
    }

    @Test
    @DisplayName("Desativa o 2FA do orientador que perdeu o celular e encerra as sessões dele")
    void deveDesativarDoisFatores() {
        Orientador orientador = new Orientador();
        orientador.setId(5L);
        orientador.setDoisFatoresAtivo(true);
        orientador.setDoisFatoresSegredo("SEGREDO");
        when(usuarioRepository.findByEmail(EMAIL_ADMIN)).thenReturn(Optional.of(administrador));
        when(usuarioRepository.findByEmail(EMAIL_ORIENTADOR)).thenReturn(Optional.of(orientador));

        correcaoExcepcionalService.desativarDoisFatores(" Orientador@teste.com ", "Perdeu o celular", EMAIL_ADMIN, IP);

        assertFalse(orientador.getDoisFatoresAtivo());
        assertNull(orientador.getDoisFatoresSegredo());
        assertEquals(1, orientador.versaoSessaoAtual());
        verify(auditoriaService).registrar(eq(administrador), eq("CORRECAO_DOIS_FATORES"),
                eq("2FA do usuário id=5 desativado. Justificativa: Perdeu o celular"), eq(IP), eq(true));
    }

    @Test
    @DisplayName("O administrador não usa a correção na própria conta")
    void naoDeveDesativarOProprio2fa() {
        administrador.setDoisFatoresAtivo(true);
        when(usuarioRepository.findByEmail(EMAIL_ADMIN)).thenReturn(Optional.of(administrador));

        assertThrows(RegraNegocioException.class,
                () -> correcaoExcepcionalService.desativarDoisFatores(EMAIL_ADMIN, "Teste", EMAIL_ADMIN, IP));
    }

    @Test
    @DisplayName("Reabre um estágio encerrado por engano, sem data de finalização e com auditoria")
    void deveCorrigirStatusFinal() {
        when(usuarioRepository.findByEmail(EMAIL_ADMIN)).thenReturn(Optional.of(administrador));
        when(estagioRepository.findById(10L)).thenReturn(Optional.of(estagio));
        when(estagioRepository.findFirstByMatriculaIdAndStatusInOrderByIdDesc(eq(2L), any())).thenReturn(Optional.empty());
        when(estagioRepository.save(estagio)).thenReturn(estagio);

        correcaoExcepcionalService.corrigirStatusEstagio(10L, StatusEstagio.ATIVO, MotivoEncerramento.REPROVACAO,
                "Desligamento lançado no aluno errado", EMAIL_ADMIN, IP);

        assertEquals(StatusEstagio.ATIVO, estagio.getStatus());
        assertNull(estagio.getMotivoEncerramento());
        assertNull(estagio.getDataFinalizacao());
        verify(auditoriaService).registrar(eq(administrador), eq("CORRECAO_STATUS_ESTAGIO"),
                eq("Estágio id=10: de ENCERRADO (DESLIGAMENTO) para ATIVO. Justificativa: Desligamento lançado no aluno errado"),
                eq(IP), eq(true));
    }

    @Test
    @DisplayName("Não reabre um estágio se a matrícula já tem outro em aberto")
    void naoDeveDeixarDoisEstagiosEmAberto() {
        Estagio outro = new Estagio();
        outro.setId(11L);
        when(usuarioRepository.findByEmail(EMAIL_ADMIN)).thenReturn(Optional.of(administrador));
        when(estagioRepository.findById(10L)).thenReturn(Optional.of(estagio));
        when(estagioRepository.findFirstByMatriculaIdAndStatusInOrderByIdDesc(eq(2L), any())).thenReturn(Optional.of(outro));

        assertThrows(RegraNegocioException.class, () -> correcaoExcepcionalService.corrigirStatusEstagio(
                10L, StatusEstagio.ATIVO, null, "Justificativa", EMAIL_ADMIN, IP));
        verify(estagioRepository, never()).save(any());
    }

    @Test
    @DisplayName("O encerramento pela correção exige o motivo")
    void naoDeveEncerrarSemMotivo() {
        estagio.setStatus(StatusEstagio.ATIVO);
        estagio.setMotivoEncerramento(null);
        when(usuarioRepository.findByEmail(EMAIL_ADMIN)).thenReturn(Optional.of(administrador));
        when(estagioRepository.findById(10L)).thenReturn(Optional.of(estagio));

        assertThrows(DadoInvalidoException.class, () -> correcaoExcepcionalService.corrigirStatusEstagio(
                10L, StatusEstagio.ENCERRADO, null, "Justificativa", EMAIL_ADMIN, IP));
        verify(auditoriaService, never()).registrar(any(), anyString(), anyString(), anyString(), eq(true));
    }
}
