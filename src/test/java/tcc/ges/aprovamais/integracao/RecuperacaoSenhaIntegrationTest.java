package tcc.ges.aprovamais.integracao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tcc.ges.aprovamais.entity.Aluno;
import tcc.ges.aprovamais.service.EmailService;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Testa o fluxo completo da recuperação de senha pela API, sem login
class RecuperacaoSenhaIntegrationTest extends IntegracaoTestBase {

    // O email não é enviado de verdade, o mock guarda o link para o teste usar
    @MockitoBean
    private EmailService emailService;

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private Aluno aluno;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        aluno = criarAluno(EMAIL_ALUNO, "RGM-001");
        aluno.setSenhaHash(passwordEncoder.encode("senhaAntiga1"));
        aluno.setPrimeiroAcesso(false);
        entityManager.flush();
    }

    private String solicitar(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/recuperacao/solicitar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String redefinir(String token, String senha) {
        return """
                {"token": "%s", "senha": "%s", "confirmacaoSenha": "%s"}
                """.formatted(token, senha, senha);
    }

    @Test
    @DisplayName("A resposta é a mesma para e-mail com e sem conta (CA01 do RF09)")
    void deveResponderIgualParaQualquerEmail() throws Exception {
        String comConta = solicitar(EMAIL_ALUNO);
        String semConta = solicitar("ninguem@teste.com");

        assertEquals(comConta, semConta);
    }

    @Test
    @DisplayName("Link do e-mail troca a senha, a antiga deixa de valer e o link não funciona de novo (CA02 e CA03)")
    void deveRedefinirSenhaPeloLink() throws Exception {
        solicitar(EMAIL_ALUNO);
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarRecuperacaoSenha(eq(EMAIL_ALUNO), anyString(), link.capture());
        String token = link.getValue().substring(link.getValue().indexOf("token=") + 6);

        // a página do link abre sem login e mostra o formulário
        mockMvc.perform(get("/redefinir-senha").param("token", token)).andExpect(status().isOk()).andExpect(content().string(containsString("Salvar nova senha")));

        mockMvc.perform(post("/api/v1/auth/recuperacao/redefinir").contentType(MediaType.APPLICATION_JSON).content(redefinir(token, "senhaNova2")))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.refresh(aluno);
        assertTrue(passwordEncoder.matches("senhaNova2", aluno.getSenhaHash()));
        assertFalse(passwordEncoder.matches("senhaAntiga1", aluno.getSenhaHash()));

        // o mesmo link não funciona de novo
        mockMvc.perform(post("/api/v1/auth/recuperacao/redefinir").contentType(MediaType.APPLICATION_JSON).content(redefinir(token, "outraSenha3")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("inválido ou expirou")));
    }

    @Test
    @DisplayName("Link inventado mostra o aviso na pAgina, sem o formulário")
    void deveAvisarLinkInvalidoNaPagina() throws Exception {
        mockMvc.perform(get("/redefinir-senha").param("token", "link-inventado"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pedir um novo link")));
    }

    @Test
    @DisplayName("Senha fraca na redefinição volta 400 no campo")
    void deveValidarSenhaNova() throws Exception {
        mockMvc.perform(post("/api/v1/auth/recuperacao/redefinir").contentType(MediaType.APPLICATION_JSON).content(redefinir("qualquer-token", "abcdefgh")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.senha").exists());
    }
}
