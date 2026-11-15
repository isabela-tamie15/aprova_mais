package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.CodigoDoisFatoresRequest;
import tcc.ges.aprovamais.dto.ConfiguracaoDoisFatoresResponse;
import tcc.ges.aprovamais.dto.StatusDoisFatoresResponse;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.security.CookieSessao;
import tcc.ges.aprovamais.security.JwtService;
import tcc.ges.aprovamais.service.DoisFatoresService;


// Verificação em duas etapas da conta do usuário logado
@Tag(name = "Verificação em duas etapas", description = "Consulta, configuração e desativação do 2FA da conta logada")
@RestController
@RequestMapping("/api/v1/conta/2fa")
@RequiredArgsConstructor
public class DoisFatoresController {

    private final DoisFatoresService doisFatoresService;
    private final JwtService jwtService;
    private final CookieSessao cookieSessao;

    // Diz se o usuário já tem o 2FA ativo
    @GetMapping
    public ResponseEntity<StatusDoisFatoresResponse> consultarStatus(Authentication authentication) {
        return ResponseEntity.ok(doisFatoresService.consultarStatus(authentication.getName()));
    }

    // Gera um novo segredo e devolve o QR Code para configurar o 2FA
    @PostMapping("/configurar")
    public ResponseEntity<ConfiguracaoDoisFatoresResponse> configurar(Authentication authentication) {
        return ResponseEntity.ok(doisFatoresService.configurar(authentication.getName()));
    }

    // Ativa o 2FA depois que o usuário confirma o código
    @PostMapping("/ativar")
    public ResponseEntity<Void> ativar(
            @Valid @RequestBody CodigoDoisFatoresRequest requisicao,
            Authentication authentication,
            HttpServletRequest request) {

        // O código no corpo é o gerado pelo app autenticador, e confirma que a configuração deu certo
        doisFatoresService.ativarPelaConta(
                authentication.getName(), requisicao.getCodigo(), request.getRemoteAddr());
        return ResponseEntity.ok().build();
    }

    // Desativa o 2FA, exigindo o código atual
    @PostMapping("/desativar")
    public ResponseEntity<Void> desativar(
            @Valid @RequestBody CodigoDoisFatoresRequest requisicao,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse respostaHttp) {

        /*
           Mesma ideia do ativar, o código é exigido aqui também para evitar
           que alguém desative o 2fa só tendo acesso à sessão
        */
        Usuario usuario = doisFatoresService.desativar(
                authentication.getName(), requisicao.getCodigo(), request.getRemoteAddr());

        // Desativar o 2FA encerra as sessões da conta, e o usuário continua logado neste navegador com um token novo
        String tokenNovo = jwtService.gerarToken(usuario.getEmail(), usuario.getPerfil().name(),
                usuario.versaoSessaoAtual());
        cookieSessao.gravar(respostaHttp, tokenNovo);
        return ResponseEntity.ok().build();
    }
}
