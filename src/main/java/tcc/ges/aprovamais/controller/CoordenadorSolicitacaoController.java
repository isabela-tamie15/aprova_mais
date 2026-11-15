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

// Decisão da coordenação sobre a troca de modalidade e o trancamento
@Tag(name = "Solicitações na coordenação", description = "Decisão da troca de modalidade e aprovação final do trancamento")
@RestController
@RequestMapping("/api/v1/coordenador/solicitacoes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COORDENADOR')")
public class CoordenadorSolicitacaoController {

    private final SolicitacaoService solicitacaoService;

    @Operation(summary = "Listar as solicitações de um curso")
    @GetMapping
    public List<SolicitacaoResponse> listar(@RequestParam Long cursoId, Authentication authentication) {
        return solicitacaoService.listarDoCurso(cursoId, authentication.getName());
    }

    @Operation(summary = "Aprovar uma solicitação")
    @PostMapping("/{id}/aprovar")
    public SolicitacaoResponse aprovar(@PathVariable Long id, Authentication authentication,
                                       HttpServletRequest httpRequest) {
        return solicitacaoService.aprovarNaCoordenacao(id, authentication.getName(), httpRequest.getRemoteAddr());
    }

    @Operation(summary = "Reprovar uma solicitação com justificativa")
    @PostMapping("/{id}/reprovar")
    public SolicitacaoResponse reprovar(@PathVariable Long id, @Valid @RequestBody RejeicaoRequest request,
                                        Authentication authentication, HttpServletRequest httpRequest) {
        return solicitacaoService.reprovarNaCoordenacao(id, authentication.getName(), request.getJustificativa(),
                httpRequest.getRemoteAddr());
    }
}
