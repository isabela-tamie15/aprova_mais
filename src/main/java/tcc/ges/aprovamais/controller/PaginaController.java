package tcc.ges.aprovamais.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import tcc.ges.aprovamais.service.EstagioService;
import tcc.ges.aprovamais.service.TrilhaService;

@Controller
@RequiredArgsConstructor
public class PaginaController {

    private final EstagioService estagioService;
    private final TrilhaService trilhaService;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/aluno/dashboard")
    @PreAuthorize("hasRole('ALUNO')")
    public String dashboardAluno(Model model, Authentication authentication) {
        String email = authentication.getName();
        model.addAttribute("estagio", estagioService.buscarEstagioAtivo(email));
        return "aluno/dashboard";}

    @GetMapping("/aluno/trilha")
    @PreAuthorize("hasRole('ALUNO')")
    public String trilhaAluno(Model model, Authentication authentication) {
        String email = authentication.getName();
        model.addAttribute("trilha", trilhaService.buscarTrilhaDoAluno(email));
        return "aluno/trilha";
    }
}