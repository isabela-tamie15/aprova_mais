package tcc.ges.aprovamais.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tcc.ges.aprovamais.dto.TrilhaResponse;
import tcc.ges.aprovamais.service.TrilhaService;

@RestController
@RequestMapping("/api/v1/aluno")
@RequiredArgsConstructor
public class TrilhaController {

    private final TrilhaService trilhaService;

    @GetMapping("/trilha")
    @PreAuthorize("hasRole('ALUNO')")
    public TrilhaResponse buscarTrilha(Authentication authentication) {
        String emailAluno = authentication.getName();
        return trilhaService.buscarTrilhaDoAluno(emailAluno);
    }
}