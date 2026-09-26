package tcc.ges.aprovamais.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.repository.ConviteRepository;
import tcc.ges.aprovamais.service.EstagioService;
import tcc.ges.aprovamais.service.TrilhaService;

// Esse controller é quem serve as páginas HTML, devolvendo o nome do template que o Thymeleaf renderiza
@Controller
@RequiredArgsConstructor
public class PaginaController {

    private final EstagioService estagioService;
    private final TrilhaService trilhaService;
    private final ConviteRepository conviteRepository;

    // rotas públicas/gerais

    // Esse é o endpoint que serve a página de login
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    // Esse é o endpoint que serve a página de consentimento dos termos
    @GetMapping("/consentimento")
    public String consentimento() {
        return "consentimento";
    }

    // Esse é o endpoint que serve a página de termos de uso
    @GetMapping("/termos")
    public String termos() {
        return "termos";
    }

    // Esse é o endpoint que serve a página de política de privacidade
    @GetMapping("/privacidade")
    public String privacidade() {
        return "privacidade";
    }

    // rotas de qualquer usuário autenticado (anyRequest().authenticated() no SecurityConfig)

    // Esse é o endpoint que serve a página de segurança da conta, onde o usuário mexe com 2fa
    @GetMapping("/conta/seguranca")
    public String segurancaConta() {
        return "conta/seguranca";
    }

    // Redireciona o usuário autenticado para a página inicial do seu perfil
    @GetMapping("/inicio")
    public String inicio(Authentication authentication) {

        /*
           Pega a primeira authority do usuário, que vem no formato ROLE_ALGO.
           O PerfilUsuario.deAuthority tira esse prefixo e devolve o enum, e
           daí a gente usa a rotaInicial que cada perfil já tem configurada.
           Assim evita ter um if gigante aqui decidindo pra onde redirecionar
        */
        String authority = authentication.getAuthorities().iterator().next().getAuthority();
        return "redirect:" + PerfilUsuario.deAuthority(authority).getRotaInicial();
    }

    // rotas do coordenador

    // Esse é o endpoint que serve o dashboard do coordenador
    @GetMapping("/coordenador/dashboard")
    @PreAuthorize("hasRole('COORDENADOR')")
    public String dashboardCoordenador() {
        return "coordenador/dashboard";
    }

    // rotas do aluno

    // Esse é o endpoint que serve o dashboard do aluno
    @GetMapping("/aluno/dashboard")
    @PreAuthorize("hasRole('ALUNO')")
    public String dashboardAluno(Model model, Authentication authentication) {

        /*
           Pega o e-mail do token, busca o estágio e joga no Model. O orElse(null)
           serve pra quando o aluno ainda não tem estágio, aí a página mostra o
           estado vazio em vez de dar erro
        */
        String email = authentication.getName();
        model.addAttribute("estagio", estagioService.buscarEstagioDoAluno(email).orElse(null));
        return "aluno/dashboard";
    }

    // Esse é o endpoint que serve a página da trilha do aluno
    @GetMapping("/aluno/trilha")
    @PreAuthorize("hasRole('ALUNO')")
    public String trilhaAluno(Model model, Authentication authentication) {

        // Busca a trilha do aluno e joga no Model pra página renderizar
        String email = authentication.getName();
        model.addAttribute("trilha", trilhaService.buscarTrilhaDoAluno(email));
        return "aluno/trilha";
    }

    // Esse é o endpoint que serve a página onde o aluno cadastra ou atualiza o estágio
    @GetMapping("/estagio")
    @PreAuthorize("hasRole('ALUNO')")
    public String paginaEstagio(Model model, Authentication authentication) {

        /*
           Essa página precisa de duas coisas no Model, o estágio atual do
           aluno (se já tiver um) e a lista de tipos disponíveis pra ele escolher
        */
        String email = authentication.getName();
        model.addAttribute("estagio", estagioService.buscarEstagioDoAluno(email).orElse(null));
        model.addAttribute("tipos", estagioService.listarTiposEstagioDisponiveis());
        return "aluno/estagio";
    }

    // rotas do orientador

    // Esse é o endpoint que serve a página de validações do orientador
    @GetMapping("/orientador/validacoes")
    @PreAuthorize("hasRole('ORIENTADOR')")
    public String paginaValidacoes(Model model, Authentication authentication) {

        // Busca os estágios pendentes desse orientador específico e joga no Model
        String email = authentication.getName();
        model.addAttribute("estagios", estagioService.listarPendentes(email));
        return "orientador/validacoes";
    }

    // rotas da secretaria

    // Esse é o endpoint que serve o dashboard da secretaria
    @GetMapping("/secretaria/dashboard")
    @PreAuthorize("hasRole('SECRETARIA')")
    public String dashboardSecretaria() {
        return "secretaria/dashboard";
    }

    // Esse é o endpoint que serve a página de envio de convite
    @GetMapping("/secretaria/enviar-convite")
    @PreAuthorize("hasRole('SECRETARIA')")
    public String enviarConvite() {
        return "secretaria/enviar-convite";
    }

    // Esse é o endpoint que serve a página com os convites enviados pela secretaria logada
    @GetMapping("/secretaria/convites")
    @PreAuthorize("hasRole('SECRETARIA')")
    public String listarConvites(Model model, Authentication authentication) {

        /*
           Busca só os convites que foram enviados pela própria secretaria,
           por isso filtra pelo e-mail do remetente que veio do token
        */
        String email = authentication.getName();
        model.addAttribute("convites",
                conviteRepository.findByRemetenteEmailOrderByCriadoEmDesc(email));
        return "secretaria/convites";
    }
}