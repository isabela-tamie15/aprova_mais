package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.AtribuicaoOrientadorRequest;
import tcc.ges.aprovamais.dto.OpcaoFiltroResponse;
import tcc.ges.aprovamais.dto.TurmaRequest;
import tcc.ges.aprovamais.dto.TurmaResponse;
import tcc.ges.aprovamais.service.OrientadorTurmaService;
import tcc.ges.aprovamais.service.TurmaService;

import java.util.List;

// Esse controller recebe as requisições do coordenador para cadastrar e alterar as turmas dos cursos dele
@Tag(name = "Cadastros da coordenação", description = "Cursos, turmas, tipos de estágio e trilhas do coordenador")
@RestController
@RequestMapping("/api/v1/coordenador/turmas")
@RequiredArgsConstructor
public class TurmaController {

    private final TurmaService turmaService;
    private final OrientadorTurmaService orientadorTurmaService;

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

    // Esse é o endpoint que lista os orientadores com conta ativa, para o coordenador escolher o da turma (RF13)
    @GetMapping("/orientadores")
    public ResponseEntity<List<OpcaoFiltroResponse>> listarOrientadores() {
        return ResponseEntity.ok(orientadorTurmaService.listarOrientadoresDisponiveis());
    }

    // Esse é o endpoint que atribui ou troca o orientador da turma, levando junto os estágios em aberto (RF13)
    @PutMapping("/{id}/orientador")
    public ResponseEntity<TurmaResponse> atribuirOrientador(@PathVariable Long id,
                                                            @Valid @RequestBody AtribuicaoOrientadorRequest request,
                                                            Authentication authentication,
                                                            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(orientadorTurmaService.atribuirOrientador(
                id, request.getOrientadorId(), authentication.getName(), httpRequest.getRemoteAddr()));
    }
}
