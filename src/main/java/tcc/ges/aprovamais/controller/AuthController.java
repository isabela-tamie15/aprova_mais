package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;
import tcc.ges.aprovamais.dto.ConfiguracaoDoisFatoresResponse;
import tcc.ges.aprovamais.dto.LoginRequest;
import tcc.ges.aprovamais.dto.LoginResponse;
import tcc.ges.aprovamais.dto.PreAutenticacaoRequest;
import tcc.ges.aprovamais.dto.VerificacaoDoisFatoresRequest;
import tcc.ges.aprovamais.security.CookieSessao;
import tcc.ges.aprovamais.security.TokenRevogadoService;
import tcc.ges.aprovamais.service.AuthService;

@Tag(name = "Autenticação", description = "Login em duas etapas, segundo fator e logout. Rotas públicas")
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenRevogadoService tokenRevogadoService;

    private final CookieSessao cookieSessao;

    // Esse é o endpoint que recebe o login e decide se o usuário já pode entrar ou se falta a segunda etapa
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest requisicao,
            HttpServletRequest request,
            HttpServletResponse respostaHttp) {

        LoginResponse loginResponse = authService.login(requisicao, request);

        /*
           Só grava o cookie se o login terminou de verdade, ou seja, sem 2fa
           pendente e sem configuração obrigatória pendente. Quando falta etapa,
           o token que vem no corpo é o de pré-autenticação, que nunca pode virar
           cookie de sessão
        */
        if (!loginResponse.isRequer2FA() && !loginResponse.isRequerConfiguracao2FA()) {
            cookieSessao.gravar(respostaHttp, loginResponse.getToken());
            return ResponseEntity.ok(loginResponse.semToken());
        }

        // Falta etapa, então devolve o token de pré-autenticação no corpo para a próxima chamada
        return ResponseEntity.ok(loginResponse);
    }

    // Esse é o endpoint que confirma o código do 2fa e finaliza o login
    @PostMapping("/2fa/verificar")
    public ResponseEntity<LoginResponse> verificarSegundoFator(
            @Valid @RequestBody VerificacaoDoisFatoresRequest requisicao,
            HttpServletRequest request,
            HttpServletResponse respostaHttp) {

        LoginResponse loginResponse = authService.verificarSegundoFator(
                requisicao.getToken(), requisicao.getCodigo(), request.getRemoteAddr());

        // Deu certo, então agora sim grava o cookie e devolve a resposta sem o token no corpo
        cookieSessao.gravar(respostaHttp, loginResponse.getToken());
        return ResponseEntity.ok(loginResponse.semToken());
    }

    // Esse é o endpoint que inicia a configuração obrigatória de 2fa para quem ainda não tem
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
            HttpServletResponse respostaHttp) {

        LoginResponse loginResponse = authService.confirmarConfiguracaoObrigatoria(
                requisicao.getToken(), requisicao.getCodigo(), request.getRemoteAddr());

        // Configurou certo, então já loga de vez e grava o cookie
        cookieSessao.gravar(respostaHttp, loginResponse.getToken());
        return ResponseEntity.ok(loginResponse.semToken());
    }

    // Esse é o endpoint de logout, ele apaga o cookie e registra a saída do usuário
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse respostaHttp,
            Authentication authentication) {

        // Encerra o token no servidor, não só no navegador
        Cookie cookieJwt = WebUtils.getCookie(request, "jwt");
        if (cookieJwt != null) {
            tokenRevogadoService.revogar(cookieJwt.getValue());
        }

        // Pede ao navegador para apagar o cookie
        cookieSessao.apagar(respostaHttp);

        /*
           Só registra no log se tiver alguém autenticado, porque o logout
           pode ser chamado também por quem já tava deslogado
        */
        if (authentication != null) {
            authService.registrarLogout(authentication.getName(), request.getRemoteAddr());
        }

        return ResponseEntity.ok().build();
    }
}
