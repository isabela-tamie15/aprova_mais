package tcc.ges.aprovamais.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.LoginRequest;
import tcc.ges.aprovamais.dto.LoginResponse;
import tcc.ges.aprovamais.service.AuditoriaService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuditoriaService auditoriaService;

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest requisicao,
            HttpServletRequest request,
            HttpServletResponse response) {

        LoginResponse loginResponse = authService.login(requisicao, request);

        if (!loginResponse.isRequer2FA()) {
            adicionarCookieJwt(response, loginResponse.getToken(), 28800);
        }

        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {

        adicionarCookieJwt(response, "", 0);

        if (authentication != null) {
            auditoriaService.registrarTentativa(
                    authentication.getName(),
                    "LOGOUT",
                    "Logout realizado com sucesso",
                    request.getRemoteAddr()
            );
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