package tcc.ges.aprovamais.auth;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.RedefinicaoSenhaRequest;
import tcc.ges.aprovamais.dto.SolicitacaoRecuperacaoRequest;
import tcc.ges.aprovamais.service.RecuperacaoSenhaService;

import java.util.Map;

// Esse controller recebe o pedido de recuperação de senha e a troca da senha pelo link do e-mail (RF09)
@Tag(name = "Autenticação", description = "Login em duas etapas, segundo fator e logout. Rotas públicas")
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/auth/recuperacao")
@RequiredArgsConstructor
public class RecuperacaoSenhaController {

    // Mesma resposta para qualquer e-mail, com ou sem conta (RN10)
    public static final String MENSAGEM_PEDIDO =
            "Se o e-mail estiver cadastrado, você vai receber em instantes um link para criar uma nova senha.";

    private final RecuperacaoSenhaService recuperacaoSenhaService;

    // Esse é o endpoint que pede o link de recuperação
    @PostMapping("/solicitar")
    public ResponseEntity<Map<String, String>> solicitar(@Valid @RequestBody SolicitacaoRecuperacaoRequest request,
                                                         HttpServletRequest httpRequest) {
        recuperacaoSenhaService.solicitar(request.getEmail(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(Map.of("mensagem", MENSAGEM_PEDIDO));
    }

    // Esse é o endpoint que troca a senha usando o token do link (link inválido, usado ou expirado volta 400)
    @PostMapping("/redefinir")
    public ResponseEntity<Map<String, String>> redefinir(@Valid @RequestBody RedefinicaoSenhaRequest request,
                                                         HttpServletRequest httpRequest) {
        recuperacaoSenhaService.redefinir(request.getToken(), request.getSenha(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(Map.of("mensagem", "Senha alterada. Entre com a nova senha."));
    }
}
