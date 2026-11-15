package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.TipoEstagioRequest;
import tcc.ges.aprovamais.dto.TipoEstagioResponse;
import tcc.ges.aprovamais.service.TipoEstagioService;

import java.util.List;

// Cadastro e alteração dos tipos de estágio dos cursos do coordenador
@Tag(name = "Cadastros da coordenação", description = "Cursos, turmas, tipos de estágio e trilhas do coordenador")
@RestController
@RequestMapping("/api/v1/coordenador/tipos-estagio")
@RequiredArgsConstructor
public class TipoEstagioController {

    private final TipoEstagioService tipoEstagioService;

    // Lista os tipos de estágio de um curso do coordenador logado
    @GetMapping
    public ResponseEntity<List<TipoEstagioResponse>> listar(@RequestParam Long cursoId,
                                                            Authentication authentication) {
        return ResponseEntity.ok(tipoEstagioService.listar(cursoId, authentication.getName()));
    }

    // Cadastra um tipo de estágio num curso do coordenador logado
    @PostMapping
    public ResponseEntity<TipoEstagioResponse> cadastrar(@Valid @RequestBody TipoEstagioRequest request,
                                                         Authentication authentication,
                                                         HttpServletRequest httpRequest) {
        TipoEstagioResponse criado = tipoEstagioService.cadastrar(
                authentication.getName(), request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    // Altera um tipo de estágio. O tipo de curso de outro coordenador volta 404
    @PutMapping("/{id}")
    public ResponseEntity<TipoEstagioResponse> atualizar(@PathVariable Long id,
                                                         @Valid @RequestBody TipoEstagioRequest request,
                                                         Authentication authentication,
                                                         HttpServletRequest httpRequest) {
        return ResponseEntity.ok(tipoEstagioService.atualizar(
                id, authentication.getName(), request, httpRequest.getRemoteAddr()));
    }
}
