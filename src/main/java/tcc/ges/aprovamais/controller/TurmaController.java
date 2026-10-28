package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.TurmaRequest;
import tcc.ges.aprovamais.dto.TurmaResponse;
import tcc.ges.aprovamais.service.TurmaService;

import java.util.List;

// Esse controller recebe as requisições do coordenador pra cadastrar e alterar as turmas dos cursos dele
@Tag(name = "Cadastros da coordenação", description = "Cursos, turmas, tipos de estágio e trilhas do coordenador")
@RestController
@RequestMapping("/api/v1/coordenador/turmas")
@RequiredArgsConstructor
public class TurmaController {

    private final TurmaService turmaService;

    // Esse é o endpoint que lista as turmas de um curso do coordenador logado
    @GetMapping
    public ResponseEntity<List<TurmaResponse>> listar(@RequestParam Long cursoId, Authentication authentication) {
        return ResponseEntity.ok(turmaService.listar(cursoId, authentication.getName()));
    }

    // Esse é o endpoint que cadastra uma turma num curso do coordenador logado
    @PostMapping
    public ResponseEntity<TurmaResponse> cadastrar(@Valid @RequestBody TurmaRequest request,
                                                   Authentication authentication,
                                                   HttpServletRequest httpRequest) {
        TurmaResponse criada = turmaService.cadastrar(
                authentication.getName(), request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(criada);
    }

    // Esse é o endpoint que altera uma turma (turma de curso de outro coordenador volta 404)
    @PutMapping("/{id}")
    public ResponseEntity<TurmaResponse> atualizar(@PathVariable Long id,
                                                   @Valid @RequestBody TurmaRequest request,
                                                   Authentication authentication,
                                                   HttpServletRequest httpRequest) {
        return ResponseEntity.ok(turmaService.atualizar(
                id, authentication.getName(), request, httpRequest.getRemoteAddr()));
    }
}
