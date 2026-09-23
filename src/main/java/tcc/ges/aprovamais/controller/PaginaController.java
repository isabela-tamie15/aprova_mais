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

    //endpoints públicos
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/consentimento")
    public String consentimento() {
        return "consentimento";
    }

    @GetMapping("/termos")
    public String termos() {
        return "termos";
    }

    @GetMapping("/privacidade")
    public String privacidade() {
        return "privacidade";
    }

    //Endpoints do aluno
    @GetMapping("/aluno/dashboard")
    @PreAuthorize("hasRole('ALUNO')")
    public String dashboardAluno(Model model, Authentication authentication) {
        String email = authentication.getName();
        model.addAttribute("estagio", estagioService.buscarEstagioDoAluno(email).orElse(null));
        return "aluno/dashboard";
    }

    @GetMapping("/aluno/trilha")
    @PreAuthorize("hasRole('ALUNO')")
    public String trilhaAluno(Model model, Authentication authentication) {
        String email = authentication.getName();
        model.addAttribute("trilha", trilhaService.buscarTrilhaDoAluno(email));
        return "aluno/trilha";
    }

    @GetMapping("/estagio")
    @PreAuthorize("hasRole('ALUNO')")
    public String paginaEstagio(Model model, Authentication authentication) {
        String email = authentication.getName();
        model.addAttribute("estagio", estagioService.buscarEstagioDoAluno(email).orElse(null));
        model.addAttribute("tipos", estagioService.listarTiposEstagioDisponiveis());
        return "aluno/estagio";
    }

    //Endpoints do orientador
    @GetMapping("/orientador/validacoes")
    @PreAuthorize("hasRole('ORIENTADOR')")
    public String paginaValidacoes(Model model, Authentication authentication) {
        String email = authentication.getName();
        model.addAttribute("estagios", estagioService.listarPendentes(email));
        return "orientador/validacoes";
    }
}