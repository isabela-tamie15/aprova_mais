package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.EncerramentoEstagioRequest;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.service.EncerramentoEstagioService;

import java.util.List;

// Estágios em aberto dos cursos do coordenador e o encerramento por desligamento
@Tag(name = "Estágios da coordenação", description = "Estágios em aberto do curso e encerramento por desligamento")
@RestController
@RequestMapping("/api/v1/coordenador/estagios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COORDENADOR')")
public class CoordenadorEstagioController {

    private final EncerramentoEstagioService encerramentoEstagioService;

    @Operation(summary = "Listar os estágios em aberto de um curso")
    @GetMapping
    public List<EstagioResponse> listar(@RequestParam Long cursoId, Authentication authentication) {
        return encerramentoEstagioService.listarEmAbertoDoCurso(cursoId, authentication.getName());
    }

    @Operation(summary = "Encerrar o estágio por desligamento")
    @PostMapping("/{estagioId}/desligar")
    public EstagioResponse desligar(@PathVariable Long estagioId, @Valid @RequestBody EncerramentoEstagioRequest request,
                                    Authentication authentication, HttpServletRequest httpRequest) {
        return encerramentoEstagioService.desligar(estagioId, authentication.getName(),
                request.getObservacao(), httpRequest.getRemoteAddr());
    }
}
