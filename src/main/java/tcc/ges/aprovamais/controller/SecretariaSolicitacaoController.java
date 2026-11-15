package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.RejeicaoRequest;
import tcc.ges.aprovamais.dto.SolicitacaoResponse;
import tcc.ges.aprovamais.service.SolicitacaoService;

import java.util.List;

// Análise dos pedidos de trancamento e de reabertura pela secretaria
@Tag(name = "Solicitações na secretaria", description = "Primeira etapa do trancamento e decisão da reabertura")
@RestController
@RequestMapping("/api/v1/secretaria/solicitacoes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SECRETARIA')")
public class SecretariaSolicitacaoController {

    private final SolicitacaoService solicitacaoService;

    @Operation(summary = "Listar os pedidos de trancamento e de reabertura")
    @GetMapping
    public List<SolicitacaoResponse> listar() {
        return solicitacaoService.listarDaSecretaria();
    }

    @Operation(summary = "Aprovar um pedido")
    @PostMapping("/{id}/aprovar")
    public SolicitacaoResponse aprovar(@PathVariable Long id, Authentication authentication,
                                       HttpServletRequest httpRequest) {
        return solicitacaoService.aprovarNaSecretaria(id, authentication.getName(), httpRequest.getRemoteAddr());
    }

    @Operation(summary = "Reprovar um pedido com justificativa")
    @PostMapping("/{id}/reprovar")
    public SolicitacaoResponse reprovar(@PathVariable Long id, @Valid @RequestBody RejeicaoRequest request,
                                        Authentication authentication, HttpServletRequest httpRequest) {
        return solicitacaoService.reprovarNaSecretaria(id, authentication.getName(), request.getJustificativa(),
                httpRequest.getRemoteAddr());
    }
}
