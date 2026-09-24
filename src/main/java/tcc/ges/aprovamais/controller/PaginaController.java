package tcc.ges.aprovamais.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import tcc.ges.aprovamais.repository.ConviteRepository;
import tcc.ges.aprovamais.service.EstagioService;
import tcc.ges.aprovamais.service.TrilhaService;

@Controller
@RequiredArgsConstructor
public class PaginaController {

    private final EstagioService estagioService;
    private final TrilhaService trilhaService;
    private final ConviteRepository conviteRepository;

    //rotas públicas/gerais
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

    //rotas do aluno
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

    //orientador
    @GetMapping("/orientador/validacoes")
    @PreAuthorize("hasRole('ORIENTADOR')")
    public String paginaValidacoes(Model model, Authentication authentication) {
        String email = authentication.getName();
        model.addAttribute("estagios", estagioService.listarPendentes(email));
        return "orientador/validacoes";
    }

    //rotas da secretaria
    @GetMapping("/secretaria/dashboard")
    @PreAuthorize("hasRole('SECRETARIA')")
    public String dashboardSecretaria() {
        return "secretaria/dashboard";
    }

    @GetMapping("/secretaria/enviar-convite")
    @PreAuthorize("hasRole('SECRETARIA')")
    public String enviarConvite() {
        return "secretaria/enviar-convite";
    }

    @GetMapping("/secretaria/convites")
    @PreAuthorize("hasRole('SECRETARIA')")
    public String listarConvites(Model model, Authentication authentication) {
        String email = authentication.getName();
        model.addAttribute("convites",
                conviteRepository.findByRemetenteEmailOrderByCriadoEmDesc(email));
        return "secretaria/convites";
    }
}