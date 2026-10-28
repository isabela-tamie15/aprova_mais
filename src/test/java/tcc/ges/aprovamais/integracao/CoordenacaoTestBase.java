package tcc.ges.aprovamais.integracao;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tcc.ges.aprovamais.entity.Coordenador;
import tcc.ges.aprovamais.entity.Curso;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/*
   Base dos testes de integração dos cadastros da coordenação. Já deixa o MockMvc
   pronto e cria dois coordenadores: o "dono" dos dados de cada teste e um outro,
   usado para conferir que um coordenador não mexe nos cursos do outro (RN21)
*/
abstract class CoordenacaoTestBase extends IntegracaoTestBase {

    protected static final String EMAIL_COORDENADOR = "coordenador@teste.com";
    protected static final String EMAIL_OUTRO_COORDENADOR = "outro.coordenador@teste.com";

    @Autowired
    private WebApplicationContext context;

    protected MockMvc mockMvc;
    protected Coordenador coordenador;
    protected Coordenador outroCoordenador;

    @BeforeEach
    void prepararCoordenacao() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        coordenador = criarCoordenador(EMAIL_COORDENADOR);
        outroCoordenador = criarCoordenador(EMAIL_OUTRO_COORDENADOR);
    }

    protected Coordenador criarCoordenador(String email) {
        Coordenador c = new Coordenador();
        c.setNome("Coordenador Teste");
        c.setEmail(email);
        c.setSenhaHash("hash-de-teste");
        c.setPerfil(PerfilUsuario.COORDENADOR);
        entityManager.persist(c);
        return c;
    }

    protected Curso criarCurso(String nome, String codigoMec, Coordenador dono) {
        Curso curso = new Curso();
        curso.setNome(nome);
        curso.setCodigoMec(codigoMec);
        curso.setCoordenador(dono);
        entityManager.persist(curso);
        entityManager.flush();
        return curso;
    }
}
