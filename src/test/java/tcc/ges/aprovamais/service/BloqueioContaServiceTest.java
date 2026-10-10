package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.LockedException;
import tcc.ges.aprovamais.entity.Aluno;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class BloqueioContaServiceTest {

    private static final String ACAO = "LOGIN";
    private static final String IP = "127.0.0.1";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private BloqueioContaService bloqueioContaService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Aluno();
        usuario.setEmail("aluno@teste.com");
        usuario.setTentativasFalhas(0);
        usuario.setContaBloqueada(false);
    }

    @Test
    @DisplayName("Conta não bloqueada segue o fluxo sem salvar nem registrar auditoria")
    void deveIgnorarVerificacaoQuandoContaNaoEstaBloqueada() {

        // act + Assert
        assertDoesNotThrow(() -> bloqueioContaService.verificarBloqueio(usuario, ACAO, IP));

        verify(usuarioRepository, never()).save(any());
        verify(auditoriaService, never())
                .registrar(any(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("Conta bloqueada dentro do prazo lança LockedException e registra auditoria")
    void deveLancarExcecaoQuandoContaBloqueadaDentroDoPrazo() {
        // arrange bloqueada há 2 minutos, o prazo é de 5
        usuario.setContaBloqueada(true);
        usuario.setTentativasFalhas(BloqueioContaService.MAX_TENTATIVAS);
        usuario.setMomentoBloqueio(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2));

        // Act
        LockedException excecao = assertThrows(LockedException.class,
                () -> bloqueioContaService.verificarBloqueio(usuario, ACAO, IP));

        // assert
        assertEquals("Conta bloqueada. Tente novamente em alguns minutos.", excecao.getMessage());
        assertTrue(usuario.getContaBloqueada());
        verify(auditoriaService).registrar(usuario, ACAO, "Conta bloqueada", IP, false);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Bloqueio com prazo vencido libera a conta e zera as tentativas")
    void deveLiberarContaQuandoPrazoDeBloqueioVenceu() {
        // Arrange: bloqueada há 5 minutos e 1 segundo, logo após o limite
        usuario.setContaBloqueada(true);
        usuario.setTentativasFalhas(BloqueioContaService.MAX_TENTATIVAS);
        usuario.setMomentoBloqueio(OffsetDateTime.now(ZoneOffset.UTC)
                .minusMinutes(BloqueioContaService.MINUTOS_BLOQUEIO)
                .minusSeconds(1));

        // Act
        bloqueioContaService.verificarBloqueio(usuario, ACAO, IP);

        // Assert
        assertFalse(usuario.getContaBloqueada());
        assertEquals(0, usuario.getTentativasFalhas());
        verify(usuarioRepository).save(usuario);
    }

    @ParameterizedTest(name = "{0} falha(s) anterior(es) -> {1} tentativa(s), bloqueada = {2}")
    @CsvSource({
            "3, 4, false",
            "4, 5, true",
            "5, 6, true"
    })
    @DisplayName("Conta é bloqueada a partir da 5th tentativa errada")
    void deveBloquearContaAoAtingirLimiteDeTentativas(int tentativasAnteriores,
                                                      int tentativasEsperadas,
                                                      boolean deveEstarBloqueada) {
        // arrange
        usuario.setTentativasFalhas(tentativasAnteriores);

        // act
        int resultado = bloqueioContaService.registrarFalha(usuario);

        // assert
        assertEquals(tentativasEsperadas, resultado);
        assertEquals(tentativasEsperadas, usuario.getTentativasFalhas());
        assertEquals(deveEstarBloqueada, usuario.getContaBloqueada());
        assertEquals(deveEstarBloqueada, usuario.getMomentoBloqueio() != null);
        verify(usuarioRepository).save(usuario);
    }
}