package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.SolicitacaoRequest;
import tcc.ges.aprovamais.dto.SolicitacaoResponse;
import tcc.ges.aprovamais.service.SolicitacaoService;

import java.util.List;

// Solicitações do aluno sobre o próprio estágio
@Tag(name = "Solicitações do aluno", description = "Troca de modalidade, trancamento, reabertura e cancelamento")
@RestController
@RequestMapping("/api/v1/aluno/solicitacoes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ALUNO')")
public class AlunoSolicitacaoController {

    private final SolicitacaoService solicitacaoService;

    @Operation(summary = "Listar as solicitações do aluno")
    @GetMapping
    public List<SolicitacaoResponse> listar(Authentication authentication) {
        return solicitacaoService.listarDoAluno(authentication.getName());
    }

    @Operation(summary = "Abrir uma solicitação")
    @PostMapping
    public SolicitacaoResponse criar(@Valid @RequestBody SolicitacaoRequest request, Authentication authentication,
                                     HttpServletRequest httpRequest) {
        return solicitacaoService.criar(authentication.getName(), request, httpRequest.getRemoteAddr());
    }

    @Operation(summary = "Cancelar uma solicitação em análise")
    @PostMapping("/{id}/cancelar")
    public SolicitacaoResponse cancelar(@PathVariable Long id, Authentication authentication,
                                        HttpServletRequest httpRequest) {
        return solicitacaoService.cancelar(id, authentication.getName(), httpRequest.getRemoteAddr());
    }
}
