package tcc.ges.aprovamais.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.service.EstagioService;

@RestController
@RequestMapping("/api/v1/aluno")
@RequiredArgsConstructor
public class EstagioController {

    private final EstagioService estagioService;

    @GetMapping("/estagio")
    @PreAuthorize("hasRole('ALUNO')")
    public EstagioResponse buscarEstagioAtivo(Authentication authentication) {
        String emailAluno = authentication.getName();
        return estagioService.buscarEstagioAtivo(emailAluno);
    }
}