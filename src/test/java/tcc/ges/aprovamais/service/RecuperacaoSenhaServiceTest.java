package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import tcc.ges.aprovamais.entity.Aluno;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.exception.DadoInvalidoException;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecuperacaoSenhaServiceTest {

    private static final String EMAIL = "aluno@teste.com";
    private static final String IP = "127.0.0.1";
    private static final String APP_URL = "http://localhost:8080";

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private BloqueioContaService bloqueioContaService;
    @InjectMocks
    private RecuperacaoSenhaService recuperacaoSenhaService;

    private Aluno usuario;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(recuperacaoSenhaService, "appUrl", APP_URL);
        ReflectionTestUtils.setField(recuperacaoSenhaService, "validadeMinutos", 30L);

        usuario = new Aluno();
        usuario.setId(1L);
        usuario.setNome("Aluno Teste");
        usuario.setEmail(EMAIL);
        usuario.setAtivo(true);
        usuario.setPrimeiroAcesso(false);
    }

    private static String sha256(String valor) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("E-mail sem conta não gera token nem e-mail, só registra a tentativa (RN10)")
    void naoDeveFazerNadaParaEmailSemConta() {
        when(usuarioRepository.findByEmail("ninguem@teste.com")).thenReturn(Optional.empty());

        recuperacaoSenhaService.solicitar("ninguem@teste.com", IP);

        verify(usuarioRepository, never()).save(any());
        verify(emailService, never()).enviarRecuperacaoSenha(anyString(), anyString(), anyString());
        verify(auditoriaService).registrarTentativa(eq("ninguem@teste.com"), eq("RECUPERACAO_SENHA_SOLICITADA"), anyString(), eq(IP));
    }

    @Test
    @DisplayName("Conta com convite pendente não recebe link de recuperação")
    void naoDeveEnviarParaContaComPrimeiroAcessoPendente() {
        usuario.setPrimeiroAcesso(true);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));

        recuperacaoSenhaService.solicitar(EMAIL, IP);

        verify(emailService, never()).enviarRecuperacaoSenha(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Conta ativa recebe o link; o banco guarda só o hash do token, com validade de 30 minutos")
    void deveGerarTokenEEnviarLink() throws Exception {
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);

        recuperacaoSenhaService.solicitar(EMAIL, IP);

        verify(emailService).enviarRecuperacaoSenha(eq(EMAIL), eq("Aluno Teste"), link.capture());
        assertTrue(link.getValue().startsWith(APP_URL + "/redefinir-senha?token="));
        String token = link.getValue().substring(link.getValue().indexOf("token=") + 6);

        assertNotEquals(token, usuario.getTokenRecuperacao());
        assertEquals(sha256(token), usuario.getTokenRecuperacao());
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        assertTrue(usuario.getExpiracaoTokenRecuperacao().isAfter(agora.plusMinutes(29)));
        assertTrue(usuario.getExpiracaoTokenRecuperacao().isBefore(agora.plusMinutes(31)));
    }

    @Test
    @DisplayName("Pedido repetido em menos de 2 minutos não manda outro e-mail")
    void naoDeveReenviarEmPoucoTempo() {
        usuario.setTokenRecuperacao("hash-anterior");
        usuario.setExpiracaoTokenRecuperacao(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(29));
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));

        recuperacaoSenhaService.solicitar(EMAIL, IP);

        verify(emailService, never()).enviarRecuperacaoSenha(anyString(), anyString(), anyString());
        assertEquals("hash-anterior", usuario.getTokenRecuperacao());
    }

    @Test
    @DisplayName("Token válido troca a senha, apaga o token e zera as tentativas de login")
    void deveRedefinirSenhaComTokenValido() throws Exception {
        usuario.setTokenRecuperacao(sha256("token-do-link"));
        usuario.setExpiracaoTokenRecuperacao(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        when(usuarioRepository.findByTokenRecuperacao(sha256("token-do-link"))).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("novaSenha1")).thenReturn("hash-bcrypt");

        recuperacaoSenhaService.redefinir("token-do-link", "novaSenha1", IP);

        assertEquals("hash-bcrypt", usuario.getSenhaHash());
        assertNull(usuario.getTokenRecuperacao());
        assertNull(usuario.getExpiracaoTokenRecuperacao());
        verify(bloqueioContaService).limparFalhas(usuario);
        verify(auditoriaService).registrar(any(Usuario.class), eq("SENHA_REDEFINIDA"), anyString(), eq(IP), eq(true));
    }

    @Test
    @DisplayName("Token vencido é recusado (CA02 do RF09)")
    void deveRecusarTokenVencido() throws Exception {
        usuario.setTokenRecuperacao(sha256("token-vencido"));
        usuario.setExpiracaoTokenRecuperacao(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(usuarioRepository.findByTokenRecuperacao(sha256("token-vencido"))).thenReturn(Optional.of(usuario));

        DadoInvalidoException excecao = assertThrows(DadoInvalidoException.class,
                () -> recuperacaoSenhaService.redefinir("token-vencido", "novaSenha1", IP));

        assertEquals(RecuperacaoSenhaService.MENSAGEM_LINK_INVALIDO, excecao.getMessage());
        verify(usuarioRepository, never()).save(any());
    }
}
