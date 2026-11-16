package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.CriterioChecklistRequest;
import tcc.ges.aprovamais.dto.CriterioChecklistResponse;
import tcc.ges.aprovamais.service.ChecklistService;

import java.util.List;

// Critérios de avaliação de cada tarefa da trilha, cadastrados pelo coordenador
@Tag(name = "Cadastros da coordenação", description = "Cursos, turmas, tipos de estágio e trilhas do coordenador")
@RestController
@RequestMapping("/api/v1/coordenador/trilhas")
@RequiredArgsConstructor
public class CriterioChecklistController {

    private final ChecklistService checklistService;

    // Critérios ativos da tarefa, na ordem do checklist
    @GetMapping("/tarefas/{tarefaId}/criterios")
    public ResponseEntity<List<CriterioChecklistResponse>> listar(@PathVariable Long tarefaId,
                                                                  Authentication authentication) {
        return ResponseEntity.ok(checklistService.listarDaTarefa(tarefaId, authentication.getName()));
    }

    // Adiciona um critério na tarefa e devolve a lista atualizada
    @PostMapping("/tarefas/{tarefaId}/criterios")
    public ResponseEntity<List<CriterioChecklistResponse>> adicionar(@PathVariable Long tarefaId,
                                                                     @Valid @RequestBody CriterioChecklistRequest request,
                                                                     Authentication authentication,
                                                                     HttpServletRequest httpRequest) {
        List<CriterioChecklistResponse> criterios = checklistService.adicionar(
                tarefaId, authentication.getName(), request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(criterios);
    }

    // Altera um critério e devolve a lista atualizada
    @PutMapping("/criterios/{criterioId}")
    public ResponseEntity<List<CriterioChecklistResponse>> alterar(@PathVariable Long criterioId,
                                                                   @Valid @RequestBody CriterioChecklistRequest request,
                                                                   Authentication authentication,
                                                                   HttpServletRequest httpRequest) {
        return ResponseEntity.ok(checklistService.alterar(
                criterioId, authentication.getName(), request, httpRequest.getRemoteAddr()));
    }

    // Remove o critério, ou só desativa quando ele já foi usado em uma correção
    @DeleteMapping("/criterios/{criterioId}")
    public ResponseEntity<Void> remover(@PathVariable Long criterioId,
                                        Authentication authentication,
                                        HttpServletRequest httpRequest) {
        checklistService.remover(criterioId, authentication.getName(), httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
