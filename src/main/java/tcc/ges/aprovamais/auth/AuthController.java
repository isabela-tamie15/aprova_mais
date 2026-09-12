package tcc.ges.aprovamais.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.LoginRequest;
import tcc.ges.aprovamais.dto.LoginResponse;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest requisicao,
            HttpServletResponse response) {
        LoginResponse loginResponse = authService.login(requisicao);

        if (!loginResponse.isRequer2FA()) {
            Cookie cookie = new Cookie("jwt", loginResponse.getToken());
            cookie.setHttpOnly(true);
            cookie.setPath("/");

            //horas 28800 segundos são equivalente a 8hrs
            cookie.setMaxAge(28800);
            response.addCookie(cookie);
        }
        return ResponseEntity.ok(loginResponse);
    }
}