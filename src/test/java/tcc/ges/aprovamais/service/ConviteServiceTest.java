package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import tcc.ges.aprovamais.entity.Administrador;
import tcc.ges.aprovamais.entity.Aluno;
import tcc.ges.aprovamais.entity.Convite;
import tcc.ges.aprovamais.entity.Coordenador;
import tcc.ges.aprovamais.entity.Curso;
import tcc.ges.aprovamais.entity.PreCadastro;
import tcc.ges.aprovamais.entity.Secretaria;
import tcc.ges.aprovamais.entity.Turma;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.entity.enums.PerfilDestino;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.entity.enums.StatusConvite;
import tcc.ges.aprovamais.exception.DadoInvalidoException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.external.EmailService;
import tcc.ges.aprovamais.repository.AlunoRepository;
import tcc.ges.aprovamais.repository.ConviteRepository;
import tcc.ges.aprovamais.repository.OrientadorRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;
import tcc.ges.aprovamais.security.HashToken;

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

// Testa o envio de convites pela hierarquia de perfis e pelo pré-cadastro (RF03, RN01, RN05, RN06, RN07)
@ExtendWith(MockitoExtension.class)
class ConviteServiceTest {

    private static final String EMAIL_DESTINO = "novo.aluno@teste.com";
    private static final String EMAIL_SECRETARIA = "secretaria@teste.com";
    private static final String EMAIL_ADMIN = "admin@teste.com";
    private static final String EMAIL_COORDENADOR = "coordenador@teste.com";
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
    @Mock
    private PreCadastroService preCadastroService;
    @InjectMocks
    private ConviteService conviteService;

    private Usuario secretaria;
    private Turma turma;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(conviteService, "appUrl", APP_URL);

        secretaria = new Secretaria();
        secretaria.setEmail(EMAIL_SECRETARIA);
        secretaria.setPerfil(PerfilUsuario.SECRETARIA);

        Curso curso = new Curso();
        curso.setNome("Engenharia de Software");
        turma = new Turma();
        turma.setId(5L);
        turma.setCurso(curso);
    }

    private PreCadastro preCadastro(PerfilDestino perfil) {
        PreCadastro preCadastro = new PreCadastro();
        preCadastro.setEmail(EMAIL_DESTINO);
        preCadastro.setNome("Maria Souza");
        preCadastro.setPerfil(perfil);
        preCadastro.setIdentificadorInstitucional("11230000001");
        preCadastro.setTurma(perfil == PerfilDestino.ALUNO ? turma : null);
        return preCadastro;
    }

    @Test
    @DisplayName("CA01: secretaria convida aluno pré-cadastrado; aluno pendente criado e convite com a turma")
    void deveConvidarAlunoPreCadastrado() {
        // arrange
        when(usuarioRepository.findByEmail(EMAIL_SECRETARIA)).thenReturn(Optional.of(secretaria));
        when(preCadastroService.buscarPorEmail(EMAIL_DESTINO)).thenReturn(Optional.of(preCadastro(PerfilDestino.ALUNO)));
        when(conviteRepository.existsByEmailAndStatus(EMAIL_DESTINO, StatusConvite.PENDENTE)).thenReturn(false);
        when(usuarioRepository.findByEmail(EMAIL_DESTINO)).thenReturn(Optional.empty());

        // act
        conviteService.enviarConvite(EMAIL_DESTINO, PerfilDestino.ALUNO, null, EMAIL_SECRETARIA, IP);

        // assert: aluno criado inativo, com o nome do pré-cadastro, esperando o primeiro acesso
        ArgumentCaptor<Aluno> alunoCaptor = ArgumentCaptor.forClass(Aluno.class);
        verify(alunoRepository).save(alunoCaptor.capture());
        Aluno aluno = alunoCaptor.getValue();
        assertEquals(EMAIL_DESTINO, aluno.getEmail());
        assertEquals("Maria Souza", aluno.getNome());
        assertEquals(PerfilUsuario.ALUNO, aluno.getPerfil());
        assertFalse(aluno.getAtivo());
        assertTrue(aluno.getPrimeiroAcesso());

        // assert: convite pendente, com a turma do pré-cadastro e validade de 24 h
        ArgumentCaptor<Convite> conviteCaptor = ArgumentCaptor.forClass(Convite.class);
        verify(conviteRepository).save(conviteCaptor.capture());
        Convite convite = conviteCaptor.getValue();
        assertEquals(StatusConvite.PENDENTE, convite.getStatus());
        assertEquals(PerfilDestino.ALUNO, convite.getPerfilDestino());
        assertEquals(secretaria, convite.getRemetente());
        assertEquals(turma, convite.getTurma());
        assertEquals(turma.getCurso(), convite.getCurso());

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        assertTrue(convite.getExpiracaoToken().isAfter(agora.plusHours(23).plusMinutes(59)));
        assertTrue(convite.getExpiracaoToken().isBefore(agora.plusHours(24).plusMinutes(1)));

        // o link do email leva o token. O banco guarda só o hash dele
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarConvite(eq(EMAIL_DESTINO), eq(EMAIL_DESTINO), linkCaptor.capture());
        String prefixo = APP_URL + "/primeiro-acesso?token=";
        assertTrue(linkCaptor.getValue().startsWith(prefixo));
        String token = linkCaptor.getValue().substring(prefixo.length());
        assertNotEquals(token, convite.getTokenConvite());
        assertEquals(HashToken.sha256(token), convite.getTokenConvite());
    }

    @Test
    @DisplayName("CA02: secretaria não pode convidar coordenador (RN01)")
    void secretariaNaoConvidaCoordenador() {
        when(usuarioRepository.findByEmail(EMAIL_SECRETARIA)).thenReturn(Optional.of(secretaria));

        assertThrows(AccessDeniedException.class,
                () -> conviteService.enviarConvite(EMAIL_DESTINO, PerfilDestino.COORDENADOR, "Fulano",
                        EMAIL_SECRETARIA, IP));

        verify(conviteRepository, never()).save(any());
        verify(emailService, never()).enviarConvite(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Administrador convida coordenador, com o nome informado no convite")
    void administradorConvidaCoordenador() {
        Administrador administrador = new Administrador();
        administrador.setEmail(EMAIL_ADMIN);
        administrador.setPerfil(PerfilUsuario.ADMINISTRADOR);
        when(usuarioRepository.findByEmail(EMAIL_ADMIN)).thenReturn(Optional.of(administrador));
        when(conviteRepository.existsByEmailAndStatus(EMAIL_DESTINO, StatusConvite.PENDENTE)).thenReturn(false);
        when(usuarioRepository.findByEmail(EMAIL_DESTINO)).thenReturn(Optional.empty());

        conviteService.enviarConvite(EMAIL_DESTINO, PerfilDestino.COORDENADOR, " Ana Lima ", EMAIL_ADMIN, IP);

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        assertInstanceOf(Coordenador.class, usuarioCaptor.getValue());
        assertEquals("Ana Lima", usuarioCaptor.getValue().getNome());
        assertEquals(PerfilUsuario.COORDENADOR, usuarioCaptor.getValue().getPerfil());
        verify(preCadastroService, never()).buscarPorEmail(anyString());
        verify(conviteRepository).save(any(Convite.class));
    }

    @Test
    @DisplayName("Coordenador convida secretaria")
    void coordenadorConvidaSecretaria() {
        Coordenador coordenador = new Coordenador();
        coordenador.setEmail(EMAIL_COORDENADOR);
        coordenador.setPerfil(PerfilUsuario.COORDENADOR);
        when(usuarioRepository.findByEmail(EMAIL_COORDENADOR)).thenReturn(Optional.of(coordenador));
        when(conviteRepository.existsByEmailAndStatus(EMAIL_DESTINO, StatusConvite.PENDENTE)).thenReturn(false);
        when(usuarioRepository.findByEmail(EMAIL_DESTINO)).thenReturn(Optional.empty());

        conviteService.enviarConvite(EMAIL_DESTINO, PerfilDestino.SECRETARIA, "Carla Dias", EMAIL_COORDENADOR, IP);

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        assertInstanceOf(Secretaria.class, usuarioCaptor.getValue());
        assertEquals(PerfilUsuario.SECRETARIA, usuarioCaptor.getValue().getPerfil());
    }

    @Test
    @DisplayName("CA03: e-mail fora do pré-cadastro é recusado")
    void deveRecusarEmailForaDoPreCadastro() {
        when(usuarioRepository.findByEmail(EMAIL_SECRETARIA)).thenReturn(Optional.of(secretaria));
        when(preCadastroService.buscarPorEmail(EMAIL_DESTINO)).thenReturn(Optional.empty());

        DadoInvalidoException excecao = assertThrows(DadoInvalidoException.class,
                () -> conviteService.enviarConvite(EMAIL_DESTINO, PerfilDestino.ALUNO, null, EMAIL_SECRETARIA, IP));

        assertTrue(excecao.getMessage().contains("pré-cadastro"));
        verify(conviteRepository, never()).save(any());
    }

    @Test
    @DisplayName("Perfil diferente do pré-cadastro é recusado")
    void deveRecusarPerfilDiferenteDoPreCadastro() {
        when(usuarioRepository.findByEmail(EMAIL_SECRETARIA)).thenReturn(Optional.of(secretaria));
        when(preCadastroService.buscarPorEmail(EMAIL_DESTINO))
                .thenReturn(Optional.of(preCadastro(PerfilDestino.ORIENTADOR)));

        assertThrows(DadoInvalidoException.class,
                () -> conviteService.enviarConvite(EMAIL_DESTINO, PerfilDestino.ALUNO, null, EMAIL_SECRETARIA, IP));

        verify(conviteRepository, never()).save(any());
    }

    @Test
    @DisplayName("CA04: convite pendente para o mesmo e-mail impede um novo envio")
    void deveLancarExcecaoQuandoJaExisteConvitePendente() {
        when(usuarioRepository.findByEmail(EMAIL_SECRETARIA)).thenReturn(Optional.of(secretaria));
        when(preCadastroService.buscarPorEmail(EMAIL_DESTINO)).thenReturn(Optional.of(preCadastro(PerfilDestino.ALUNO)));
        when(conviteRepository.existsByEmailAndStatus(EMAIL_DESTINO, StatusConvite.PENDENTE)).thenReturn(true);

        IllegalStateException excecao = assertThrows(IllegalStateException.class,
                () -> conviteService.enviarConvite(EMAIL_DESTINO, PerfilDestino.ALUNO, null, EMAIL_SECRETARIA, IP));

        assertEquals("Já existe um convite pendente para o email: " + EMAIL_DESTINO, excecao.getMessage());
        verify(conviteRepository, never()).save(any());
        verify(emailService, never()).enviarConvite(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Quem já tem conta ativa não é convidado de novo")
    void deveRecusarContaAtiva() {
        Aluno ativo = new Aluno();
        ativo.setEmail(EMAIL_DESTINO);
        ativo.setPerfil(PerfilUsuario.ALUNO);
        ativo.setPrimeiroAcesso(false);
        when(usuarioRepository.findByEmail(EMAIL_SECRETARIA)).thenReturn(Optional.of(secretaria));
        when(preCadastroService.buscarPorEmail(EMAIL_DESTINO)).thenReturn(Optional.of(preCadastro(PerfilDestino.ALUNO)));
        when(conviteRepository.existsByEmailAndStatus(EMAIL_DESTINO, StatusConvite.PENDENTE)).thenReturn(false);
        when(usuarioRepository.findByEmail(EMAIL_DESTINO)).thenReturn(Optional.of(ativo));

        assertThrows(IllegalStateException.class,
                () -> conviteService.enviarConvite(EMAIL_DESTINO, PerfilDestino.ALUNO, null, EMAIL_SECRETARIA, IP));

        verify(conviteRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reenvio para quem ainda não concluiu o cadastro não duplica o usuário")
    void deveReenviarConviteSemDuplicarUsuarioPendente() {
        Aluno pendente = new Aluno();
        pendente.setEmail(EMAIL_DESTINO);
        pendente.setPerfil(PerfilUsuario.ALUNO);
        pendente.setPrimeiroAcesso(true);
        when(usuarioRepository.findByEmail(EMAIL_SECRETARIA)).thenReturn(Optional.of(secretaria));
        when(preCadastroService.buscarPorEmail(EMAIL_DESTINO)).thenReturn(Optional.of(preCadastro(PerfilDestino.ALUNO)));
        when(conviteRepository.existsByEmailAndStatus(EMAIL_DESTINO, StatusConvite.PENDENTE)).thenReturn(false);
        when(usuarioRepository.findByEmail(EMAIL_DESTINO)).thenReturn(Optional.of(pendente));

        conviteService.enviarConvite(EMAIL_DESTINO, PerfilDestino.ALUNO, null, EMAIL_SECRETARIA, IP);

        verify(conviteRepository).save(any(Convite.class));
        verify(emailService).enviarConvite(eq(EMAIL_DESTINO), eq(EMAIL_DESTINO), anyString());
        verify(alunoRepository, never()).save(any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Token vencido é marcado como EXPIRADO e lança exceção")
    void deveMarcarConviteComoExpiradoQuandoTokenVenceu() {
        Convite convite = new Convite();
        convite.setEmail(EMAIL_DESTINO);
        convite.setTokenConvite("token-vencido");
        convite.setStatus(StatusConvite.PENDENTE);
        convite.setExpiracaoToken(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(conviteRepository.findByTokenConvite(HashToken.sha256("token-vencido"))).thenReturn(Optional.of(convite));

        IllegalStateException excecao = assertThrows(IllegalStateException.class,
                () -> conviteService.validarToken("token-vencido"));

        assertEquals("Este convite expirou. Solicite um novo convite a quem enviou", excecao.getMessage());
        assertEquals(StatusConvite.EXPIRADO, convite.getStatus());
        verify(conviteRepository).atualizarStatus(convite.getId(), StatusConvite.EXPIRADO);
        verify(conviteRepository, never()).save(any());
    }

    @Test
    @DisplayName("Token que não existe, vazio ou grande demais responde como convite inválido")
    void deveRecusarTokenInvalido() {
        when(conviteRepository.findByTokenConvite(HashToken.sha256("nao-existe"))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> conviteService.validarToken("nao-existe"));
        assertThrows(ResourceNotFoundException.class, () -> conviteService.validarToken(""));
        assertThrows(ResourceNotFoundException.class, () -> conviteService.validarToken("x".repeat(201)));
    }
}
