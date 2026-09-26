package tcc.ges.aprovamais.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.ConfiguracaoDoisFatoresResponse;
import tcc.ges.aprovamais.dto.LoginRequest;
import tcc.ges.aprovamais.dto.LoginResponse;
import tcc.ges.aprovamais.dto.PreAutenticacaoRequest;
import tcc.ges.aprovamais.dto.VerificacaoDoisFatoresRequest;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // Diz se a aplicação tá rodando com SSL, usado pra decidir se o cookie vai com Secure ou não
    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    // Esse é o endpoint que recebe o login e decide se o usuário já pode entrar ou se falta a segunda etapa
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest requisicao,
            HttpServletRequest request,
            HttpServletResponse response) {

        LoginResponse loginResponse = authService.login(requisicao, request);

        /*
           Só grava o cookie se o login terminou de verdade, ou seja, sem 2fa
           pendente e sem configuração obrigatória pendente. Quando falta etapa,
           o token que vem no corpo é o de pré-autenticação, que nunca pode virar
           cookie de sessão
        */
        if (!loginResponse.isRequer2FA() && !loginResponse.isRequerConfiguracao2FA()) {
            adicionarCookieJwt(response, loginResponse.getToken(), 28800);
            return ResponseEntity.ok(loginResponse.semToken());
        }

        // Falta etapa, então devolve o token de pré-autenticação no corpo pra próxima chamada
        return ResponseEntity.ok(loginResponse);
    }

    // Esse é o endpoint que confirma o código do 2fa e finaliza o login
    @PostMapping("/2fa/verificar")
    public ResponseEntity<LoginResponse> verificarSegundoFator(
            @Valid @RequestBody VerificacaoDoisFatoresRequest requisicao,
            HttpServletRequest request,
            HttpServletResponse response) {

        LoginResponse loginResponse = authService.verificarSegundoFator(
                requisicao.getToken(), requisicao.getCodigo(), request.getRemoteAddr());

        // Deu certo, então agora sim grava o cookie e devolve a resposta sem o token no corpo
        adicionarCookieJwt(response, loginResponse.getToken(), 28800);
        return ResponseEntity.ok(loginResponse.semToken());
    }

    // Esse é o endpoint que inicia a configuração obrigatória de 2fa pra quem ainda não tem
    @PostMapping("/2fa/configuracao/iniciar")
    public ResponseEntity<ConfiguracaoDoisFatoresResponse> iniciarConfiguracaoObrigatoria(
            @Valid @RequestBody PreAutenticacaoRequest requisicao) {

        return ResponseEntity.ok(authService.iniciarConfiguracaoObrigatoria(requisicao.getToken()));
    }

    // Esse é o endpoint que confirma o código e finaliza a configuração obrigatória de 2fa
    @PostMapping("/2fa/configuracao/confirmar")
    public ResponseEntity<LoginResponse> confirmarConfiguracaoObrigatoria(
            @Valid @RequestBody VerificacaoDoisFatoresRequest requisicao,
            HttpServletRequest request,
            HttpServletResponse response) {

        LoginResponse loginResponse = authService.confirmarConfiguracaoObrigatoria(
                requisicao.getToken(), requisicao.getCodigo(), request.getRemoteAddr());

        // Configurou certo, então já loga de vez e grava o cookie
        adicionarCookieJwt(response, loginResponse.getToken(), 28800);
        return ResponseEntity.ok(loginResponse.semToken());
    }

    // Esse é o endpoint de logout, ele apaga o cookie e registra a saída do usuário
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {

        /*
           Grava um cookie com valor vazio e Max-Age 0, que é a forma padrão
           de dizer pro navegador apagar o cookie
        */
        adicionarCookieJwt(response, "", 0);

        /*
           Só registra no log se tiver alguém autenticado, porque o logout
           pode ser chamado também por quem já tava deslogado
        */
        if (authentication != null) {
            authService.registrarLogout(authentication.getName(), request.getRemoteAddr());
        }

        return ResponseEntity.ok().build();
    }

    // Esse é o auxiliar que monta e adiciona o cookie do JWT na resposta
    private void adicionarCookieJwt(HttpServletResponse response, String valor, int maxAge) {

        // Em produção com SSL, adiciona o Secure pro cookie só trafegar em HTTPS
        String secure = sslEnabled ? "; Secure" : "";

        /*
           Monta na mão em vez de usar a classe Cookie do Java porque ela não
           expõe o atributo SameSite. O HttpOnly impede leitura por JavaScript,
           o Path=/ faz valer pra toda aplicação e o SameSite=Strict evita
           envio em requisições de outros sites
        */
        String cookie = String.format(
                "jwt=%s; HttpOnly; Path=/; Max-Age=%d; SameSite=Strict%s",
                valor, maxAge, secure
        );
        response.addHeader("Set-Cookie", cookie);
    }
}
