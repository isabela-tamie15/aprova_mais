package tcc.ges.aprovamais.integracao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tcc.ges.aprovamais.entity.Aluno;
import tcc.ges.aprovamais.service.ConsentimentoService;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// LGPD - quem não aceitou a versão atual dos termos vai para o consentimento antes de abrir as telas
class ConsentimentoIntegrationTest extends IntegracaoTestBase {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;
    private Aluno aluno;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        aluno = criarAluno(EMAIL_ALUNO, "RGM-001");
        // o painel do aluno busca a matrícula ativa, então o aluno precisa estar numa turma
        criarMatricula(aluno, criarTurma());
    }

    @Test
    @DisplayName("Sem o aceite dos termos, a tela do aluno manda para o consentimento")
    void semAceiteDeveIrParaOConsentimento() throws Exception {
        aluno.setConsentimentoDado(false);
        entityManager.flush();

        mockMvc.perform(get("/aluno/dashboard").with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/consentimento"));
    }

    @Test
    @DisplayName("Aceite de uma versão antiga dos termos também manda para o consentimento")
    void versaoAntigaDeveIrParaOConsentimento() throws Exception {
        aluno.setVersaoConsentimento("0.9");
        entityManager.flush();

        mockMvc.perform(get("/aluno/dashboard").with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(redirectedUrl("/consentimento"));
    }

    @Test
    @DisplayName("Com o aceite da versão atual, a tela abre normalmente")
    void comAceiteDeveAbrirATela() throws Exception {
        aluno.setVersaoConsentimento(ConsentimentoService.VERSAO_TERMOS_ATUAL);
        entityManager.flush();

        mockMvc.perform(get("/aluno/dashboard").with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(status().isOk());
    }
}