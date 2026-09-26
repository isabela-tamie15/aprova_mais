package tcc.ges.aprovamais.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tcc.ges.aprovamais.dto.TrilhaResponse;
import tcc.ges.aprovamais.service.TrilhaService;

// Esse controller é quem recebe a requisição do aluno pra buscar a trilha dele
@RestController
@RequestMapping("/api/v1/aluno")
@RequiredArgsConstructor
public class TrilhaController {

    private final TrilhaService trilhaService;

    // Esse é o endpoint que devolve a trilha de tarefas do aluno logado
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