package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tcc.ges.aprovamais.dto.TrilhaResponse;
import tcc.ges.aprovamais.service.TrilhaService;

// Trilha de tarefas do aluno
@Tag(name = "Trilha", description = "Consulta da trilha de tarefas do estágio do aluno")
@RestController
@RequestMapping("/api/v1/aluno")
@RequiredArgsConstructor
public class TrilhaController {

    private final TrilhaService trilhaService;

    // Devolve a trilha de tarefas do aluno logado
    @GetMapping("/trilha")
    @PreAuthorize("hasRole('ALUNO')")
    public TrilhaResponse buscarTrilha(Authentication authentication) {

        /*
           O e-mail vem do token, não da URL nem do corpo da requisição,
           então o aluno não consegue pedir a trilha de outra pessoa
        */
        String emailAluno = authentication.getName();
        return trilhaService.buscarTrilhaDoAluno(emailAluno);
    }
}