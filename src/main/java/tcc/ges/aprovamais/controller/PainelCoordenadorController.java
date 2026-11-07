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
import tcc.ges.aprovamais.dto.PainelCoordenadorResponse;
import tcc.ges.aprovamais.service.PainelCoordenadorService;

/*
   Dashboard do coordenador: devolve os números dos cartões e dos gráficos já prontos.
   A rota já é protegida pelo SecurityConfig (/api/v1/coordenador/**), e o @PreAuthorize
   repete a verificação aqui, para termos as duas camadas de segurança
*/
@Tag(name = "Painel da coordenação", description = "Indicadores e gráficos do dashboard do coordenador")
@RestController
@RequestMapping("/api/v1/coordenador/painel")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COORDENADOR')")
public class PainelCoordenadorController {

    private final PainelCoordenadorService painelCoordenadorService;

    // O cursoId é opcional: sem ele, o painel mostra todos os cursos do coordenador logado
    @Operation(summary = "Consultar os números do dashboard do coordenador (RF50)")
    @GetMapping
    public ResponseEntity<PainelCoordenadorResponse> consultar(@RequestParam(required = false) Long cursoId,
                                                               Authentication authentication) {
        return ResponseEntity.ok(painelCoordenadorService.montarPainel(authentication.getName(), cursoId));
    }
}
