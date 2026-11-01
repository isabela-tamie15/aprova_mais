package tcc.ges.aprovamais.integracao;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import tcc.ges.aprovamais.entity.Aluno;
import tcc.ges.aprovamais.entity.Coordenador;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/*
   Base dos testes de integração por HTTP real. Sobe a aplicação inteira numa
   porta aleatória, com o perfil test (H2 em memória), e faz as chamadas com
   RestClient, passando por segurança, controller, service, repository e banco.

   Nada é mockado: os usuários entram pelo endpoint de login de verdade e o
   JWT é lido do cookie que a API devolve. Como os dados ficam gravados no
   banco (não há rollback entre requisições HTTP), todo campo único é gerado
   por execução com System.nanoTime(), então os testes não dependem de ordem
*/
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class ApiIntegrationTestBase {

    protected static final String SENHA = "SenhaDeTeste@123";

    protected static final ParameterizedTypeReference<Map<String, Object>> MAPA =
            new ParameterizedTypeReference<>() {};
    protected static final ParameterizedTypeReference<List<Map<String, Object>>> LISTA =
            new ParameterizedTypeReference<>() {};

    private final GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    protected UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    protected RestClient client;

    @BeforeEach
    void configurarCliente() {
        // Não lança exceção em 4xx/5xx, para os cenários de erro serem conferidos com asserções
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> {})
                .build();
    }

    // ===== Dados únicos por execução =====

    // Código curto e único (nanoTime em base 36), cabe no limite de 20 caracteres do e-MEC e da turma
    protected static String codigoUnico() {
        return Long.toString(System.nanoTime(), 36).toUpperCase();
    }

    protected static String emailUnico(String prefixo) {
        return prefixo + "." + System.nanoTime() + "@it.aprovamais.com";
    }

    // ===== Usuários e login pelos endpoints reais =====

    // Cria o coordenador e passa pelas duas etapas do login, devolvendo o JWT
    protected String criarCoordenadorELogar(String email) {
        String segredo = criarCoordenadorCom2fa(email);
        return extrairJwt(verificarSegundoFator(fazerLogin(email), segredo));
    }

    // Coordenador já com 2FA ativo, porque o perfil exige. Devolve o segredo para gerar o código no login
    protected String criarCoordenadorCom2fa(String email) {
        String segredo = googleAuthenticator.createCredentials().getKey();

        Coordenador coordenador = new Coordenador();
        coordenador.setNome("Coordenador Integracao");
        coordenador.setEmail(email);
        coordenador.setSenhaHash(passwordEncoder.encode(SENHA));
        coordenador.setPerfil(PerfilUsuario.COORDENADOR);
        coordenador.setDoisFatoresAtivo(true);
        coordenador.setDoisFatoresSegredo(segredo);
        coordenador.setPrimeiroAcesso(false);
        coordenador.setConsentimentoDado(true);
        usuarioRepository.save(coordenador);
        return segredo;
    }

    // Aluno sem 2FA (é opcional para o perfil), então o login já devolve o cookie
    protected String criarAlunoELogar(String email) {
        Aluno aluno = new Aluno();
        aluno.setNome("Aluno Integracao");
        aluno.setEmail(email);
        aluno.setSenhaHash(passwordEncoder.encode(SENHA));
        aluno.setPerfil(PerfilUsuario.ALUNO);
        aluno.setRgm("RGM" + System.nanoTime());
        aluno.setPrimeiroAcesso(false);
        aluno.setConsentimentoDado(true);
        usuarioRepository.save(aluno);

        return extrairJwt(client.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "senha", SENHA))
                .retrieve()
                .toEntity(MAPA));
    }

    // Primeira etapa do login do coordenador: devolve o token de pré-autenticação
    protected String fazerLogin(String email) {
        ResponseEntity<Map<String, Object>> resposta = client.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "senha", SENHA))
                .retrieve()
                .toEntity(MAPA);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resposta.getBody().get("token");
    }

    // Segunda etapa: envia o código TOTP calculado com o segredo do coordenador
    protected ResponseEntity<Map<String, Object>> verificarSegundoFator(String tokenPreAutenticacao,
                                                                        String segredo) {
        String codigo = String.format("%06d", googleAuthenticator.getTotpPassword(segredo));

        return client.post().uri("/api/v1/auth/2fa/verificar")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("token", tokenPreAutenticacao, "codigo", codigo))
                .retrieve()
                .toEntity(MAPA);
    }

    // O JWT vem só no cookie HttpOnly "jwt", nunca no corpo
    protected static String extrairJwt(ResponseEntity<?> respostaLogin) {
        assertThat(respostaLogin.getStatusCode()).isEqualTo(HttpStatus.OK);

        String cookie = respostaLogin.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .filter(valor -> valor.startsWith("jwt="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("A resposta do login não trouxe o cookie jwt"));

        return cookie.substring("jwt=".length(), cookie.indexOf(';'));
    }

    // ===== Helpers de cursos e turmas, criados pelo próprio endpoint =====

    protected static Map<String, Object> buildCursoRequest(String nome, String codigoMec) {
        return Map.of("nome", nome, "codigoMec", codigoMec);
    }

    protected static Map<String, Object> buildTurmaRequest(Long cursoId, String semestre, String identificador,
                                                           String periodo, String modalidade) {
        return Map.of(
                "cursoId", cursoId,
                "semestre", semestre,
                "identificador", identificador,
                "periodo", periodo,
                "modalidade", modalidade
        );
    }

    protected Long criarCurso(String token, String nome, String codigoMec) {
        ResponseEntity<Map<String, Object>> resposta = client.post().uri("/api/v1/coordenador/cursos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildCursoRequest(nome, codigoMec))
                .retrieve()
                .toEntity(MAPA);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return idDe(resposta.getBody());
    }

    protected Long criarTurma(String token, Long cursoId, String semestre, String identificador) {
        ResponseEntity<Map<String, Object>> resposta = client.post().uri("/api/v1/coordenador/turmas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildTurmaRequest(cursoId, semestre, identificador, "NOTURNO", "PRESENCIAL"))
                .retrieve()
                .toEntity(MAPA);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return idDe(resposta.getBody());
    }

    protected static Long idDe(Map<String, Object> corpo) {
        return ((Number) corpo.get("id")).longValue();
    }

    // Erro de validação (400) traz um mapa "campos" com a mensagem de cada campo inválido
    @SuppressWarnings("unchecked")
    protected static Map<String, Object> camposDe(Map<String, Object> corpo) {
        return (Map<String, Object>) corpo.get("campos");
    }
}
