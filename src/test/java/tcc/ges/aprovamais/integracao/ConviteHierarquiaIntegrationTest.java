package tcc.ges.aprovamais.integracao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tcc.ges.aprovamais.entity.Aluno;
import tcc.ges.aprovamais.entity.Convite;
import tcc.ges.aprovamais.entity.Coordenador;
import tcc.ges.aprovamais.entity.Curso;
import tcc.ges.aprovamais.entity.Matricula;
import tcc.ges.aprovamais.entity.PreCadastro;
import tcc.ges.aprovamais.entity.Secretaria;
import tcc.ges.aprovamais.entity.Turma;
import tcc.ges.aprovamais.entity.enums.Modalidade;
import tcc.ges.aprovamais.entity.enums.PerfilDestino;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.entity.enums.Periodo;
import tcc.ges.aprovamais.entity.enums.StatusConvite;
import tcc.ges.aprovamais.entity.enums.StatusMatricula;
import tcc.ges.aprovamais.external.EmailService;
import tcc.ges.aprovamais.repository.ConviteRepository;
import tcc.ges.aprovamais.repository.MatriculaRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Testa o convite pela hierarquia e o aceite com a matrícula criada na turma
class ConviteHierarquiaIntegrationTest extends IntegracaoTestBase {

    private static final String EMAIL_SECRETARIA = "secretaria@teste.com";
    private static final String EMAIL_COORDENADOR = "coordenador@teste.com";
    private static final String EMAIL_ALUNO_NOVO = "maria.souza@teste.com";
    private static final String RGM = "11230000001";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ConviteRepository conviteRepository;
    @Autowired
    private MatriculaRepository matriculaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    // O email não é enviado de verdade nos testes
    @MockitoBean
    private EmailService emailService;

    private MockMvc mockMvc;
    private Turma turma;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Secretaria secretaria = new Secretaria();
        secretaria.setNome("Secretaria Teste");
        secretaria.setEmail(EMAIL_SECRETARIA);
        secretaria.setSenhaHash("hash-de-teste");
        secretaria.setPerfil(PerfilUsuario.SECRETARIA);
        entityManager.persist(secretaria);

        Coordenador coordenador = new Coordenador();
        coordenador.setNome("Coordenador Teste");
        coordenador.setEmail(EMAIL_COORDENADOR);
        coordenador.setSenhaHash("hash-de-teste");
        coordenador.setPerfil(PerfilUsuario.COORDENADOR);
        entityManager.persist(coordenador);

        Curso curso = new Curso();
        curso.setNome("Engenharia de Software");
        curso.setCodigoMec("1105");
        curso.setCoordenador(coordenador);
        entityManager.persist(curso);

        turma = new Turma();
        turma.setCurso(curso);
        turma.setSemestre("2026/2");
        turma.setIdentificador("8B");
        turma.setPeriodo(Periodo.NOTURNO);
        turma.setModalidade(Modalidade.PRESENCIAL);
        entityManager.persist(turma);

        PreCadastro preCadastro = new PreCadastro();
        preCadastro.setNome("Maria Souza");
        preCadastro.setEmail(EMAIL_ALUNO_NOVO);
        preCadastro.setPerfil(PerfilDestino.ALUNO);
        preCadastro.setIdentificadorInstitucional(RGM);
        preCadastro.setTurma(turma);
        preCadastro.setCoordenador(coordenador);
        entityManager.persist(preCadastro);
        entityManager.flush();
    }

    private ResultActions convidar(String corpo, String emailRemetente, String perfilRemetente) throws Exception {
        return mockMvc.perform(post("/api/v1/convites")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo)
                .with(user(emailRemetente).roles(perfilRemetente)));
    }

    @Test
    @DisplayName("CA02 do RF03: secretaria que tenta convidar coordenador recebe 403")
    void secretariaNaoConvidaCoordenador() throws Exception {
        convidar("{\"emailDestino\":\"novo.coord@teste.com\",\"perfil\":\"COORDENADOR\",\"nome\":\"Novo Coordenador\"}",
                EMAIL_SECRETARIA, "SECRETARIA")
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CA03 do RF03: e-mail fora do pré-cadastro volta 400 com o motivo")
    void emailForaDoPreCadastro() throws Exception {
        convidar("{\"emailDestino\":\"desconhecido@teste.com\",\"perfil\":\"ALUNO\"}", EMAIL_SECRETARIA, "SECRETARIA")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(containsString("pré-cadastro")));
    }

    @Test
    @DisplayName("Coordenador e secretaria sem nome voltam 400 com o erro no campo nome")
    void conviteAdministrativoExigeNome() throws Exception {
        convidar("{\"emailDestino\":\"nova.sec@teste.com\",\"perfil\":\"SECRETARIA\"}", EMAIL_COORDENADOR, "COORDENADOR")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nome").value("Informe o nome completo"));
    }

    @Test
    @DisplayName("Fluxo completo: convite do aluno pré-cadastrado, recusa do segundo envio e matrícula criada no aceite")
    void fluxoCompletoDoAluno() throws Exception {
        String corpo = "{\"emailDestino\":\"" + EMAIL_ALUNO_NOVO + "\",\"perfil\":\"ALUNO\"}";

        // convite enviado
        convidar(corpo, EMAIL_SECRETARIA, "SECRETARIA").andExpect(status().isOk());

        // segundo envio com o primeiro ainda pendente volta 409
        convidar(corpo, EMAIL_SECRETARIA, "SECRETARIA").andExpect(status().isConflict());

        Convite convite = conviteRepository.findByEmailAndStatus(EMAIL_ALUNO_NOVO, StatusConvite.PENDENTE).orElseThrow();
        assertEquals(turma.getId(), convite.getTurma().getId());

        // aceite com senha válida ativa a conta e cria a matrícula na turma do pré-cadastro
        mockMvc.perform(post("/primeiro-acesso/aceitar")
                        .param("token", convite.getTokenConvite())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\":\"senhaForte1\",\"confirmacaoSenha\":\"senhaForte1\"}")).andExpect(status().isOk());

        entityManager.flush();
        Aluno aluno = (Aluno) usuarioRepository.findByEmail(EMAIL_ALUNO_NOVO).orElseThrow();
        assertTrue(aluno.getAtivo());
        assertEquals(RGM, aluno.getRgm());
        Matricula matricula = matriculaRepository.findByAlunoIdAndTurmaId(aluno.getId(), turma.getId()).orElseThrow();
        assertEquals(StatusMatricula.ATIVA, matricula.getStatus());

    }
}
