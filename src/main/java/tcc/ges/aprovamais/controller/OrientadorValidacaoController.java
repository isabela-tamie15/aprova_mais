package tcc.ges.aprovamais.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.dto.RejeicaoRequest;
import tcc.ges.aprovamais.service.EstagioService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orientador/validacoes")
@RequiredArgsConstructor
public class OrientadorValidacaoController {

    private final EstagioService estagioService;

    @GetMapping
    public ResponseEntity<List<EstagioResponse>> listarPendentes() {
        return ResponseEntity.ok(estagioService.listarPendentes());
    }

    @PostMapping("/{id}/aprovar")
    public ResponseEntity<EstagioResponse> aprovar(@PathVariable Long id, Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(estagioService.aprovar(id, email));
    }

    @PostMapping("/{id}/rejeitar")
    public ResponseEntity<EstagioResponse> rejeitar(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody RejeicaoRequest request) {

        String email = authentication.getName();
        return ResponseEntity.ok(estagioService.rejeitar(id, email, request.getJustificativa()));
    }
}