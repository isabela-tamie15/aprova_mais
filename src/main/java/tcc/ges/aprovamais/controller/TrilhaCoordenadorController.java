package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.TarefaRequest;
import tcc.ges.aprovamais.dto.TrilhaDetalheResponse;
import tcc.ges.aprovamais.dto.TrilhaRequest;
import tcc.ges.aprovamais.service.TrilhaCoordenadorService;

// Esse controller recebe as requisições do coordenador pra montar as trilhas e as tarefas dos tipos de estágio
@Tag(name = "Cadastros da coordenação", description = "Cursos, turmas, tipos de estágio e trilhas do coordenador")
@RestController
@RequestMapping("/api/v1/coordenador/trilhas")
@RequiredArgsConstructor
public class TrilhaCoordenadorController {

    private final TrilhaCoordenadorService trilhaCoordenadorService;

    // Esse é o endpoint que devolve a trilha de um tipo de estágio, ou 204 se o tipo ainda não tem trilha
    @GetMapping
    public ResponseEntity<TrilhaDetalheResponse> buscarPorTipo(@RequestParam Long tipoEstagioId,
                                                               Authentication authentication) {
        return trilhaCoordenadorService.buscarPorTipo(tipoEstagioId, authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // Esse é o endpoint que cria a trilha de um tipo de estágio (o tipo que já tem trilha volta 409)
    @PostMapping
    public ResponseEntity<TrilhaDetalheResponse> criar(@Valid @RequestBody TrilhaRequest request,
                                                       Authentication authentication,
                                                       HttpServletRequest httpRequest) {
        TrilhaDetalheResponse criada = trilhaCoordenadorService.criar(
                authentication.getName(), request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(criada);
    }

    // Esse é o endpoint que altera o nome e a descrição da trilha
    @PutMapping("/{id}")
    public ResponseEntity<TrilhaDetalheResponse> atualizar(@PathVariable Long id,
                                                           @Valid @RequestBody TrilhaRequest request,
                                                           Authentication authentication,
                                                           HttpServletRequest httpRequest) {
        return ResponseEntity.ok(trilhaCoordenadorService.atualizar(
                id, authentication.getName(), request, httpRequest.getRemoteAddr()));
    }

    // Esse é o endpoint que adiciona uma tarefa na trilha e devolve a trilha atualizada
    @PostMapping("/{id}/tarefas")
    public ResponseEntity<TrilhaDetalheResponse> adicionarTarefa(@PathVariable Long id,
                                                                 @Valid @RequestBody TarefaRequest request,
                                                                 Authentication authentication,
                                                                 HttpServletRequest httpRequest) {
        TrilhaDetalheResponse trilha = trilhaCoordenadorService.adicionarTarefa(
                id, authentication.getName(), request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(trilha);
    }

    // Esse é o endpoint que altera uma tarefa e devolve a trilha atualizada
    @PutMapping("/tarefas/{tarefaId}")
    public ResponseEntity<TrilhaDetalheResponse> atualizarTarefa(@PathVariable Long tarefaId,
                                                                 @Valid @RequestBody TarefaRequest request,
                                                                 Authentication authentication,
                                                                 HttpServletRequest httpRequest) {
        return ResponseEntity.ok(trilhaCoordenadorService.atualizarTarefa(
                tarefaId, authentication.getName(), request, httpRequest.getRemoteAddr()));
    }

    // Esse é o endpoint que remove uma tarefa da trilha
    @DeleteMapping("/tarefas/{tarefaId}")
    public ResponseEntity<Void> removerTarefa(@PathVariable Long tarefaId,
                                              Authentication authentication,
                                              HttpServletRequest httpRequest) {
        trilhaCoordenadorService.removerTarefa(tarefaId, authentication.getName(), httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
