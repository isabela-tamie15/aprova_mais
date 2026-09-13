package tcc.ges.aprovamais.auth;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.LoginRequest;
import tcc.ges.aprovamais.dto.LoginResponse;

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
            HttpServletResponse response) {

        LoginResponse loginResponse = authService.login(requisicao);

        if (!loginResponse.isRequer2FA()) {
            adicionarCookieJwt(response, loginResponse.getToken(), 28800);
        }

        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        adicionarCookieJwt(response, "", 0);
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