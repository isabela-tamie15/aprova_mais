package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tcc.ges.aprovamais.dto.FiltroAuditoriaRequest;
import tcc.ges.aprovamais.dto.PaginaAuditoriaResponse;
import tcc.ges.aprovamais.service.ConsultaAuditoriaService;

import java.util.List;

// Esse controller é quem expõe a consulta da auditoria, só para o Adm
@Tag(name = "Auditoria", description = "Consulta dos registros de auditoria pelo Administrador")
@RestController
@RequestMapping("/api/v1/admin/auditoria")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@RequiredArgsConstructor
public class AuditoriaController {

    private final ConsultaAuditoriaService consultaAuditoriaService;

    // Esse é o endpoint que lista os registros com os filtros da query string
    @Operation(summary = "Consulta a auditoria com filtros por e-mail, ação, resultado e período")
    @GetMapping
    public ResponseEntity<PaginaAuditoriaResponse> consultar(@Valid @ModelAttribute FiltroAuditoriaRequest filtro,
                                                             Authentication authentication,
                                                             HttpServletRequest request) {
        return ResponseEntity.ok(consultaAuditoriaService.consultar(filtro, authentication.getName(),
                request.getRemoteAddr()));

    }

    // Esse é o endpoint que devolve as ações já registradas, para montar o filtro da tela
    @Operation(summary = "Lista as ações que já aparecem na auditoria")
    @GetMapping("/acoes")
    public ResponseEntity<List<String>> listarAcoes() {

        return ResponseEntity.ok(consultaAuditoriaService.listarAcoes());

    }
}
