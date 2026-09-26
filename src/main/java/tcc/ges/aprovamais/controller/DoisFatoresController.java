package tcc.ges.aprovamais.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.CodigoDoisFatoresRequest;
import tcc.ges.aprovamais.dto.ConfiguracaoDoisFatoresResponse;
import tcc.ges.aprovamais.dto.StatusDoisFatoresResponse;
import tcc.ges.aprovamais.service.DoisFatoresService;


// Esse controller é quem recebe as requisições de gerenciamento de 2fa da conta do usuário logado
@RestController
@RequestMapping("/api/v1/conta/2fa")
@RequiredArgsConstructor
public class DoisFatoresController {

    private final DoisFatoresService doisFatoresService;

    // Esse é o endpoint que diz se o usuário já tem 2fa ativado ou não
    @GetMapping
    public ResponseEntity<StatusDoisFatoresResponse> consultarStatus(Authentication authentication) {
        return ResponseEntity.ok(doisFatoresService.consultarStatus(authentication.getName()));
    }

    // Esse é o endpoint que gera um novo segredo e devolve o QR Code pra configurar o 2fa
    @PostMapping("/configurar")
    public ResponseEntity<ConfiguracaoDoisFatoresResponse> configurar(Authentication authentication) {
        return ResponseEntity.ok(doisFatoresService.configurar(authentication.getName()));
    }

    // Esse é o endpoint que ativa o 2fa de vez, depois que o usuário confirma o código
    @PostMapping("/ativar")
    public ResponseEntity<Void> ativar(
            @Valid @RequestBody CodigoDoisFatoresRequest requisicao,
            Authentication authentication,
            HttpServletRequest request) {

        /*
           E-mail do token e IP da requisição. O código vem no corpo,
           é o que o app autenticador gerou pra confirmar que o usuário
           conseguiu configurar direito
        */
        doisFatoresService.ativarPelaConta(
                authentication.getName(), requisicao.getCodigo(), request.getRemoteAddr());
        return ResponseEntity.ok().build();
    }

    // Esse é o endpoint que desativa o 2fa, exige o código atual pra confirmar
    @PostMapping("/desativar")
    public ResponseEntity<Void> desativar(
            @Valid @RequestBody CodigoDoisFatoresRequest requisicao,
            Authentication authentication,
            HttpServletRequest request) {

        /*
           Mesma ideia do ativar, o código é exigido aqui também pra evitar
           que alguém desative o 2fa só tendo acesso à sessão
        */
        doisFatoresService.desativar(
                authentication.getName(), requisicao.getCodigo(), request.getRemoteAddr());
        return ResponseEntity.ok().build();
    }
}
