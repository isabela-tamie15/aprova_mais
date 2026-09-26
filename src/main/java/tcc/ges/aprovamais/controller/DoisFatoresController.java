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


@RestController
@RequestMapping("/api/v1/conta/2fa")
@RequiredArgsConstructor
public class DoisFatoresController {

    private final DoisFatoresService doisFatoresService;

    @GetMapping
    public ResponseEntity<StatusDoisFatoresResponse> consultarStatus(Authentication authentication) {
        return ResponseEntity.ok(doisFatoresService.consultarStatus(authentication.getName()));
    }

    @PostMapping("/configurar")
    public ResponseEntity<ConfiguracaoDoisFatoresResponse> configurar(Authentication authentication) {
        return ResponseEntity.ok(doisFatoresService.configurar(authentication.getName()));
    }

    @PostMapping("/ativar")
    public ResponseEntity<Void> ativar(
            @Valid @RequestBody CodigoDoisFatoresRequest requisicao,
            Authentication authentication,
            HttpServletRequest request) {

        doisFatoresService.ativarPelaConta(
                authentication.getName(), requisicao.getCodigo(), request.getRemoteAddr());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/desativar")
    public ResponseEntity<Void> desativar(
            @Valid @RequestBody CodigoDoisFatoresRequest requisicao,
            Authentication authentication,
            HttpServletRequest request) {

        doisFatoresService.desativar(
                authentication.getName(), requisicao.getCodigo(), request.getRemoteAddr());
        return ResponseEntity.ok().build();
    }
}
