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

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest requisicao,
            HttpServletRequest request,
            HttpServletResponse response) {

        LoginResponse loginResponse = authService.login(requisicao, request);

        // Quando há segunda etapa, o campo token contém o token de pré-autenticação,
        // que nunca deve ser gravado como cookie de sessão
        if (!loginResponse.isRequer2FA() && !loginResponse.isRequerConfiguracao2FA()) {
            adicionarCookieJwt(response, loginResponse.getToken(), 28800);
            return ResponseEntity.ok(loginResponse.semToken());
        }

        // Segunda etapa pendente, corpo leva o token de pré-autenticação (não é o JWT)
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/2fa/verificar")
    public ResponseEntity<LoginResponse> verificarSegundoFator(
            @Valid @RequestBody VerificacaoDoisFatoresRequest requisicao,
            HttpServletRequest request,
            HttpServletResponse response) {

        LoginResponse loginResponse = authService.verificarSegundoFator(
                requisicao.getToken(), requisicao.getCodigo(), request.getRemoteAddr());

        adicionarCookieJwt(response, loginResponse.getToken(), 28800);
        return ResponseEntity.ok(loginResponse.semToken());
    }

    @PostMapping("/2fa/configuracao/iniciar")
    public ResponseEntity<ConfiguracaoDoisFatoresResponse> iniciarConfiguracaoObrigatoria(
            @Valid @RequestBody PreAutenticacaoRequest requisicao) {

        return ResponseEntity.ok(authService.iniciarConfiguracaoObrigatoria(requisicao.getToken()));
    }

    @PostMapping("/2fa/configuracao/confirmar")
    public ResponseEntity<LoginResponse> confirmarConfiguracaoObrigatoria(
            @Valid @RequestBody VerificacaoDoisFatoresRequest requisicao,
            HttpServletRequest request,
            HttpServletResponse response) {

        LoginResponse loginResponse = authService.confirmarConfiguracaoObrigatoria(
                requisicao.getToken(), requisicao.getCodigo(), request.getRemoteAddr());

        adicionarCookieJwt(response, loginResponse.getToken(), 28800);
        return ResponseEntity.ok(loginResponse.semToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        adicionarCookieJwt(response, "", 0);

        if (authentication != null) {
            authService.registrarLogout(authentication.getName(), request.getRemoteAddr());
        }

        return ResponseEntity.ok().build();
    }


    private void adicionarCookieJwt(HttpServletResponse response, String valor, int maxAge) {
        String secure = sslEnabled ? "; Secure" : "";
        String cookie = String.format(
                "jwt=%s; HttpOnly; Path=/; Max-Age=%d; SameSite=Strict%s",
                valor, maxAge, secure
        );
        response.addHeader("Set-Cookie", cookie);
    }
}