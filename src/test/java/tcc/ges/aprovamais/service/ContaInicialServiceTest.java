package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import tcc.ges.aprovamais.entity.Administrador;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Testa a criação da conta inicial do Administrador
@ExtendWith(MockitoExtension.class)
class ContaInicialServiceTest {

    private static final String EMAIL = "admin@teste.com";
    private static final String SENHA = "senhaForte123";

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditoriaService auditoriaService;
    @InjectMocks
    private ContaInicialService contaInicialService;

    @Test
    @DisplayName("CA01: sem administrador no banco, cria a conta com os dados das variáveis de ambiente")
    void deveCriarAdministradorQuandoNaoExiste() {
        when(usuarioRepository.existsByPerfil(PerfilUsuario.ADMINISTRADOR)).thenReturn(false);
        when(usuarioRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(SENHA)).thenReturn("hash-bcrypt");

        contaInicialService.criarAdministradorInicialSeNecessario(EMAIL, SENHA, "Administrador");

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario criado = captor.getValue();

        assertInstanceOf(Administrador.class, criado);
        assertEquals(PerfilUsuario.ADMINISTRADOR, criado.getPerfil());
        assertEquals(EMAIL, criado.getEmail());
        assertEquals("hash-bcrypt", criado.getSenhaHash());
        assertTrue(criado.getAtivo());

        // Sem 2FA ativo, o login deve obrigar a configurar
        assertFalse(criado.getDoisFatoresAtivo());
        verify(auditoriaService).registrar(eq(criado), eq("CONTA_INICIAL_CRIADA"), anyString(), isNull(), eq(true));
    }

    @Test
    @DisplayName("CA02: com administrador já cadastrado, nenhuma conta nova é criada")
    void naoDeveCriarQuandoJaExisteAdministrador() {
        when(usuarioRepository.existsByPerfil(PerfilUsuario.ADMINISTRADOR)).thenReturn(true);

        contaInicialService.criarAdministradorInicialSeNecessario(EMAIL, SENHA, "Administrador");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sem e-mail ou senha configurados, não cria a conta e a aplicação segue")
    void naoDeveCriarSemCredenciais() {
        when(usuarioRepository.existsByPerfil(PerfilUsuario.ADMINISTRADOR)).thenReturn(false);

        contaInicialService.criarAdministradorInicialSeNecessario("", "", "Administrador");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Senha com menos de 8 caracteres não cria a conta")
    void naoDeveCriarComSenhaCurta() {
        when(usuarioRepository.existsByPerfil(PerfilUsuario.ADMINISTRADOR)).thenReturn(false);

        contaInicialService.criarAdministradorInicialSeNecessario(EMAIL, "curta", "Administrador");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("E-mail que já pertence a outro usuário não cria a conta")
    void naoDeveCriarComEmailEmUso() {
        when(usuarioRepository.existsByPerfil(PerfilUsuario.ADMINISTRADOR)).thenReturn(false);
        when(usuarioRepository.existsByEmail(EMAIL)).thenReturn(true);

        contaInicialService.criarAdministradorInicialSeNecessario(EMAIL, SENHA, "Administrador");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("O perfil Administrador exige 2FA (RN12)")
    void administradorExigeDoisFatores() {
        assertTrue(PerfilUsuario.ADMINISTRADOR.exigeDoisFatores());
    }
}
