package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tcc.ges.aprovamais.dto.FiltroPainel;
import tcc.ges.aprovamais.dto.PainelCoordenadorResponse;
import tcc.ges.aprovamais.service.PainelCoordenadorService;

/*
   Dashboard do coordenador, com os números dos cartões e dos gráficos já prontos.
   O @PreAuthorize repete a proteção da rota que já está no SecurityConfig
*/
@Tag(name = "Painel da coordenação", description = "Indicadores e gráficos do dashboard do coordenador")
@RestController
@RequestMapping("/api/v1/coordenador/painel")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COORDENADOR')")
public class PainelCoordenadorController {

    private final PainelCoordenadorService painelCoordenadorService;

    // Todos os filtros são opcionais, e sem eles o painel mostra todos os cursos do coordenador logado
    @Operation(summary = "Consultar os números do dashboard do coordenador, com filtros de curso, semestre, turma e orientador")
    @GetMapping
    public ResponseEntity<PainelCoordenadorResponse> consultar(@RequestParam(required = false) Long cursoId,
                                                               @RequestParam(required = false) String semestre,
                                                               @RequestParam(required = false) Long turmaId,
                                                               @RequestParam(required = false) Long orientadorId,
                                                               Authentication authentication) {
        FiltroPainel filtro = new FiltroPainel(cursoId, semestre, turmaId, orientadorId);
        return ResponseEntity.ok(painelCoordenadorService.montarPainel(authentication.getName(), filtro));
    }
}
