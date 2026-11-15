package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.CursoRequest;
import tcc.ges.aprovamais.dto.CursoResponse;
import tcc.ges.aprovamais.service.CursoService;

import java.util.List;

// Cadastro e alteração dos cursos do coordenador
@Tag(name = "Cadastros da coordenação", description = "Cursos, turmas, tipos de estágio e trilhas do coordenador")
@RestController
@RequestMapping("/api/v1/coordenador/cursos")
@RequiredArgsConstructor
public class CursoController {

    private final CursoService cursoService;

    // Lista os cursos do coordenador logado
    @GetMapping
    public ResponseEntity<List<CursoResponse>> listar(Authentication authentication) {
        return ResponseEntity.ok(cursoService.listarDoCoordenador(authentication.getName()));
    }

    // Cadastra um curso novo, com o coordenador logado como responsável
    @PostMapping
    public ResponseEntity<CursoResponse> cadastrar(@Valid @RequestBody CursoRequest request,
                                                   Authentication authentication,
                                                   HttpServletRequest httpRequest) {
        CursoResponse criado = cursoService.cadastrar(
                authentication.getName(), request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    // Altera um curso do coordenador logado. O curso de outro coordenador volta 404
    @PutMapping("/{id}")
    public ResponseEntity<CursoResponse> atualizar(@PathVariable Long id,
                                                   @Valid @RequestBody CursoRequest request,
                                                   Authentication authentication,
                                                   HttpServletRequest httpRequest) {
        return ResponseEntity.ok(cursoService.atualizar(
                id, authentication.getName(), request, httpRequest.getRemoteAddr()));
    }
}
