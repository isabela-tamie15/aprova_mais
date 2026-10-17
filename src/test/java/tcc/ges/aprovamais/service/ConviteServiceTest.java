package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tcc.ges.aprovamais.entity.Aluno;
import tcc.ges.aprovamais.entity.Convite;
import tcc.ges.aprovamais.entity.Secretaria;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.entity.enums.PerfilDestino;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.entity.enums.StatusConvite;
import tcc.ges.aprovamais.repository.AlunoRepository;
import tcc.ges.aprovamais.repository.ConviteRepository;
import tcc.ges.aprovamais.repository.OrientadorRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConviteServiceTest {

    private static final String EMAIL_DESTINO = "novo.aluno@teste.com";
    private static final String EMAIL_SECRETARIA = "secretaria@teste.com";
    private static final String IP = "127.0.0.1";
    private static final String APP_URL = "http://localhost:8080";

    @Mock
    private ConviteRepository conviteRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private AlunoRepository alunoRepository;
    @Mock
    private OrientadorRepository orientadorRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @InjectMocks
    private ConviteService conviteService;
    private Usuario secretaria;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(conviteService, "appUrl", APP_URL);

        secretaria = new Secretaria();
        secretaria.setEmail(EMAIL_SECRETARIA);
        secretaria.setPerfil(PerfilUsuario.SECRETARIA);
    }

    @Test
    @DisplayName("E-mail novo cria aluno pendente, convite válido por 24 h e envia o e-mail")
    void deveEnviarConviteECriarAlunoPendente() {
        // arrange
        when(conviteRepository.existsByEmailAndStatus(EMAIL_DESTINO, StatusConvite.PENDENTE))
                .thenReturn(false);
        when(usuarioRepository.findByEmail(EMAIL_SECRETARIA)).thenReturn(Optional.of(secretaria));
        when(usuarioRepository.existsByEmail(EMAIL_DESTINO)).thenReturn(false);

        // act
        conviteService.enviarConvite(EMAIL_DESTINO, PerfilDestino.ALUNO,
                EMAIL_SECRETARIA, null, null, IP);

        // assert é aluno criado inativo esperando o primeiro acesso
        ArgumentCaptor<Aluno> alunoCaptor = ArgumentCaptor.forClass(Aluno.class);
        verify(alunoRepository).save(alunoCaptor.capture());
        Aluno aluno = alunoCaptor.getValue();
        assertEquals(EMAIL_DESTINO, aluno.getEmail());
        assertEquals(PerfilUsuario.ALUNO, aluno.getPerfil());
        assertFalse(aluno.getAtivo());
        assertTrue(aluno.getPrimeiroAcesso());

        // assert convite pendente, vinculado à secretaria e expira em 24H
        ArgumentCaptor<Convite> conviteCaptor = ArgumentCaptor.forClass(Convite.class);
        verify(conviteRepository).save(conviteCaptor.capture());
        Convite convite = conviteCaptor.getValue();
        assertEquals(StatusConvite.PENDENTE, convite.getStatus());
        assertEquals(PerfilDestino.ALUNO, convite.getPerfilDestino());
        assertEquals(secretaria, convite.getRemetente());
        assertNotNull(convite.getTokenConvite());

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        assertTrue(convite.getExpiracaoToken().isAfter(agora.plusHours(23).plusMinutes(59)));
        assertTrue(convite.getExpiracaoToken().isBefore(agora.plusHours(24).plusMinutes(1)));

        //assert, o link do email leva o token do convite
        verify(emailService).enviarConvite(EMAIL_DESTINO, EMAIL_DESTINO,
                APP_URL + "/primeiro-acesso?token=" + convite.getTokenConvite());
        verify(orientadorRepository, never()).save(any());
    }

    @Test
    @DisplayName("Convite pendente para o mesmo e-mail impede um novo envio")
    void deveLancarExcecaoQuandoJaExisteConvitePendente() {
        // arrange
        when(conviteRepository.existsByEmailAndStatus(EMAIL_DESTINO, StatusConvite.PENDENTE))
                .thenReturn(true);

        // act
        IllegalStateException excecao = assertThrows(IllegalStateException.class,
                () -> conviteService.enviarConvite(EMAIL_DESTINO, PerfilDestino.ALUNO,
                        EMAIL_SECRETARIA, null, null, IP));

        // assert
        assertEquals("Já existe um convite pendente para o email: " + EMAIL_DESTINO,
                excecao.getMessage());
        verify(conviteRepository, never()).save(any());
        verify(emailService, never()).enviarConvite(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Token vencido é marcado como EXPIRADO e lança exceção")
    void deveMarcarConviteComoExpiradoQuandoTokenVenceu() {
        // ARrange
        Convite convite = new Convite();
        convite.setEmail(EMAIL_DESTINO);
        convite.setTokenConvite("token-vencido");
        convite.setStatus(StatusConvite.PENDENTE);
        convite.setExpiracaoToken(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(conviteRepository.findByTokenConvite("token-vencido")).thenReturn(Optional.of(convite));

        // act
        IllegalStateException excecao = assertThrows(IllegalStateException.class,
                () -> conviteService.validarToken("token-vencido"));

        // Assert
        assertEquals("Este convite expirou. Solicite um novo convite à secretaria",
                excecao.getMessage());
        assertEquals(StatusConvite.EXPIRADO, convite.getStatus());
        verify(conviteRepository).save(convite);
    }

    @Test
    @DisplayName("Reenvio para e-mail que já tem usuário não duplica o cadastro")
    void deveReenviarConviteSemDuplicarUsuarioExistente() {
        // Arrange
        when(conviteRepository.existsByEmailAndStatus(EMAIL_DESTINO, StatusConvite.PENDENTE))
                .thenReturn(false);
        when(usuarioRepository.findByEmail(EMAIL_SECRETARIA)).thenReturn(Optional.of(secretaria));
        when(usuarioRepository.existsByEmail(EMAIL_DESTINO)).thenReturn(true);

        // Act
        conviteService.enviarConvite(EMAIL_DESTINO, PerfilDestino.ALUNO,
                EMAIL_SECRETARIA, null, null, IP);

        // Assert
        verify(conviteRepository).save(any(Convite.class));
        verify(emailService).enviarConvite(eq(EMAIL_DESTINO), eq(EMAIL_DESTINO), anyString());
        verify(alunoRepository, never()).save(any());
        verify(orientadorRepository, never()).save(any());
    }
}