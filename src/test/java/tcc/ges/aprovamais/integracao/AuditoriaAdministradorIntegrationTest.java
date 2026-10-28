package tcc.ges.aprovamais.integracao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tcc.ges.aprovamais.entity.Administrador;
import tcc.ges.aprovamais.entity.LogAuditoria;
import tcc.ges.aprovamais.entity.enums.PerfilAuditoria;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.service.ConsultaAuditoriaService;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Testa o perfil Administrador (RF01) e a consulta da auditoria (RF46) pela API
class AuditoriaAdministradorIntegrationTest extends IntegracaoTestBase {

    private static final String EMAIL_ADMIN = "admin@teste.com";
    private static final String SENHA_ADMIN = "senhaForte123";
    private static final String URL = "/api/v1/admin/auditoria";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Administrador administrador = new Administrador();
        administrador.setNome("Administrador");
        administrador.setEmail(EMAIL_ADMIN);
        administrador.setSenhaHash(passwordEncoder.encode(SENHA_ADMIN));
        administrador.setPerfil(PerfilUsuario.ADMINISTRADOR);
        administrador.setPrimeiroAcesso(false);
        entityManager.persist(administrador);

        // Os registros são gravados direto, porque a AuditoriaService é mock nos testes
        registrar("orientador@teste.com", PerfilAuditoria.ORIENTADOR, "ESTAGIO_REJEITADO", true);
        registrar("orientador@teste.com", PerfilAuditoria.ORIENTADOR, "ESTAGIO_APROVADO", true);
        registrar("outro.orientador@teste.com", PerfilAuditoria.ORIENTADOR, "ESTAGIO_REJEITADO", true);
        registrar("aluno@teste.com", PerfilAuditoria.ALUNO, "LOGIN_FALHA", false);
        entityManager.flush();
    }

    private void registrar(String email, PerfilAuditoria perfil, String acao, boolean sucesso) {
        LogAuditoria registro = new LogAuditoria();
        registro.setEmailTentativa(email);
        registro.setPerfil(perfil);
        registro.setAcao(acao);
        registro.setDetalhes("registro de teste");
        registro.setIpOrigem("127.0.0.1");
        registro.setSucesso(sucesso);
        entityManager.persist(registro);
    }

    @Test
    @DisplayName("CA01 (RF46): filtro por e-mail e pela ação de rejeição traz só as rejeições desse usuário")
    void deveFiltrarPorEmailEAcao() throws Exception {
        // outro.orientador@ também rejeitou, mas o e-mail completo busca só o usuário exato
        mockMvc.perform(get(URL).param("email", "Orientador@teste.com").param("acao", "ESTAGIO_REJEITADO").with(user(EMAIL_ADMIN).roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(1))
                .andExpect(jsonPath("$.itens[0].email").value("orientador@teste.com"))
                .andExpect(jsonPath("$.itens[0].acao").value("ESTAGIO_REJEITADO"));
    }

    @Test
    @DisplayName("Sem o @, o e-mail vale como trecho e traz os dois orientadores")
    void deveFiltrarPorTrechoDoEmail() throws Exception {
        mockMvc.perform(get(URL).param("email", "orientador").param("acao", "ESTAGIO_REJEITADO").with(user(EMAIL_ADMIN).roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(2)))
                .andExpect(jsonPath("$.itens[*].acao").value(everyItem(is("ESTAGIO_REJEITADO"))));
    }

    @Test
    @DisplayName("Filtro por resultado traz só as falhas")
    void deveFiltrarPorResultado() throws Exception {
        mockMvc.perform(get(URL).param("sucesso", "false").with(user(EMAIL_ADMIN).roles("ADMINISTRADOR"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].acao").value("LOGIN_FALHA"));
    }

    @Test
    @DisplayName("A própria consulta é registrada na auditoria")
    void deveRegistrarAConsulta() throws Exception {
        mockMvc.perform(get(URL).param("acao", "LOGIN_FALHA").with(user(EMAIL_ADMIN).roles("ADMINISTRADOR")))
                .andExpect(status().isOk());

        verify(auditoriaService).registrar(any(), eq(ConsultaAuditoriaService.ACAO_CONSULTA),
                contains("LOGIN_FALHA"), anyString(), eq(true));
    }

    @Test
    @DisplayName("Período com início depois do fim volta 400 com o erro no campo")
    void deveRecusarPeriodoInvertido() throws Exception {
        mockMvc.perform(get(URL).param("inicio", "2026-10-25").param("fim", "2026-10-01").with(user(EMAIL_ADMIN).roles("ADMINISTRADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.periodoValido").exists());
    }

    @Test
    @DisplayName("CA02 (RF46): coordenador recebe 403 na auditoria")
    void coordenadorNaoAcessaAuditoria() throws Exception {
        mockMvc.perform(get(URL).with(user("coordenador@teste.com").roles("COORDENADOR")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/auditoria").with(user("coordenador@teste.com").roles("COORDENADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CA03 (RF01): o primeiro login do administrador exige configurar o 2FA")
    void primeiroLoginDoAdministradorExigeConfigurarDoisFatores() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + EMAIL_ADMIN + "\",\"senha\":\"" + SENHA_ADMIN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requerConfiguracao2FA").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("A página inicial do administrador é a auditoria")
    void inicioLevaParaAuditoria() throws Exception {
        mockMvc.perform(get("/inicio").with(user(EMAIL_ADMIN).roles("ADMINISTRADOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/auditoria"));
    }
}
