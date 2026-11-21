package tcc.ges.aprovamais.integracao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tcc.ges.aprovamais.entity.AceiteTermos;
import tcc.ges.aprovamais.entity.Aluno;
import tcc.ges.aprovamais.repository.AceiteTermosRepository;
import tcc.ges.aprovamais.service.ConsentimentoService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Quem não aceitou a versão atual dos termos vai para a tela de consentimento antes de abrir as outras telas
class ConsentimentoIntegrationTest extends IntegracaoTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AceiteTermosRepository aceiteTermosRepository;
    @Autowired
    private ConsentimentoService consentimentoService;

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

    @Test
    @DisplayName("Cada aceite grava uma linha no histórico, e um aceite novo não apaga o anterior")
    void aceiteDeveFicarNoHistorico() {
        aluno.setVersaoConsentimento("1.1");
        aceiteTermosRepository.save(new AceiteTermos(aluno, "1.1", OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)));

        consentimentoService.registrarAceite(EMAIL_ALUNO, "127.0.0.1");
        entityManager.flush();

        List<AceiteTermos> historico = aceiteTermosRepository.findByUsuarioIdOrderByAceitoEmDesc(aluno.getId());
        assertThat(historico).hasSize(2);
        assertThat(historico.get(0).getVersao()).isEqualTo(ConsentimentoService.VERSAO_TERMOS_ATUAL);
        assertThat(historico.get(1).getVersao()).isEqualTo("1.1");
    }

    @Test
    @DisplayName("A tela de aceite mostra a versão vigente dos documentos")
    void telaDeAceiteDeveMostrarAVersao() throws Exception {
        aluno.setConsentimentoDado(false);
        entityManager.flush();

        mockMvc.perform(get("/consentimento").with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(ConsentimentoService.VERSAO_TERMOS_ATUAL)));
    }

    @Test
    @DisplayName("Quem aceitou antes do histórico ganha uma linha com o aceite guardado, uma vez só")
    void aceiteAnteriorDeveEntrarNoHistorico() {
        OffsetDateTime aceitoEm = OffsetDateTime.now(ZoneOffset.UTC).minusDays(60);
        aluno.setVersaoConsentimento("1.1");
        aluno.setDataConsentimento(aceitoEm);
        entityManager.flush();

        consentimentoService.registrarAceitesAnteriores();
        consentimentoService.registrarAceitesAnteriores();
        entityManager.flush();

        List<AceiteTermos> historico = aceiteTermosRepository.findByUsuarioIdOrderByAceitoEmDesc(aluno.getId());
        assertThat(historico).hasSize(1);
        assertThat(historico.get(0).getVersao()).isEqualTo("1.1");
    }
}
