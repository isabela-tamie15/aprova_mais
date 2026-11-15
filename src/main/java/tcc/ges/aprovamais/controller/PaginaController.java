package tcc.ges.aprovamais.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.dto.ValidacaoEstagioResponse;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.service.ConviteService;
import tcc.ges.aprovamais.service.EstagioService;
import tcc.ges.aprovamais.service.TrilhaService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Páginas HTML renderizadas pelo Thymeleaf
@Controller
@RequiredArgsConstructor
public class PaginaController {

    private final EstagioService estagioService;
    private final TrilhaService trilhaService;
    private final ConviteService conviteService;

    // rotas públicas/gerais

    // Página de login
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    // Página de consentimento dos termos
    @GetMapping("/consentimento")
    public String consentimento() {
        return "consentimento";
    }

    // Página dos termos de uso
    @GetMapping("/termos")
    public String termos() {
        return "termos";
    }

    // Página da política de privacidade
    @GetMapping("/privacidade")
    public String privacidade() {
        return "privacidade";
    }

    // rotas de qualquer usuário autenticado (anyRequest().authenticated() no SecurityConfig)

    // Página de segurança da conta, com o 2FA
    @GetMapping("/conta/seguranca")
    public String segurancaConta() {
        return "conta/seguranca";
    }

    // Redireciona o usuário autenticado para a página inicial do seu perfil
    @GetMapping("/inicio")
    public String inicio(Authentication authentication) {

        /*
           A primeira authority vem no formato ROLE_ALGO. O PerfilUsuario.deAuthority devolve o perfil,
           e a rotaInicial dele diz para onde redirecionar
        */
        String authority = authentication.getAuthorities().iterator().next().getAuthority();
        return "redirect:" + PerfilUsuario.deAuthority(authority).getRotaInicial();
    }

    // rotas do coordenador

    // Dashboard do coordenador
    @GetMapping("/coordenador/dashboard")
    @PreAuthorize("hasRole('COORDENADOR')")
    public String dashboardCoordenador() {
        return "coordenador/dashboard";
    }

    // rotas do aluno

    // Dashboard do aluno
    @GetMapping("/aluno/dashboard")
    @PreAuthorize("hasRole('ALUNO')")
    public String dashboardAluno(Model model, Authentication authentication) {

        // Aluno sem estágio recebe nulo, e a página mostra o estado vazio
        String email = authentication.getName();
        model.addAttribute("estagio", estagioService.buscarEstagioDoAluno(email).orElse(null));
        model.addAttribute("historico", estagioService.listarHistoricoDoAluno(email));

        // Estágios que já saíram de aberto (concluídos ou encerrados) aparecem na lista de anteriores
        List<EstagioResponse> estagiosAnteriores = new ArrayList<>();
        for (EstagioResponse estagio : estagioService.listarEstagiosDoAluno(email)) {
            boolean concluido = estagio.getStatus().equals("CONCLUIDO");
            boolean encerrado = estagio.getStatus().equals("ENCERRADO");
            if (concluido || encerrado) {
                estagiosAnteriores.add(estagio);
            }
        }
        model.addAttribute("estagiosAnteriores", estagiosAnteriores);
        return "aluno/dashboard";
    }

    // Página da trilha do aluno
    @GetMapping("/aluno/trilha")
    @PreAuthorize("hasRole('ALUNO')")
    public String trilhaAluno(Model model, Authentication authentication) {

        // Busca a trilha do aluno e joga no Model para a página renderizar
        String email = authentication.getName();
        model.addAttribute("trilha", trilhaService.buscarTrilhaDoAluno(email));
        return "aluno/trilha";
    }

    // Página em que o aluno cadastra ou atualiza o estágio
    @GetMapping("/estagio")
    @PreAuthorize("hasRole('ALUNO')")
    public String paginaEstagio(Model model, Authentication authentication) {

        // A página recebe o estágio atual do aluno, quando existe, e os tipos que ele pode escolher
        String email = authentication.getName();
        model.addAttribute("estagio", estagioService.buscarEstagioDoAluno(email).orElse(null));
        model.addAttribute("tipos", estagioService.listarTiposEstagioDisponiveis(email));
        return "aluno/estagio";
    }

    // rotas do orientador

    // Página de validações do orientador
    @GetMapping("/orientador/validacoes")
    @PreAuthorize("hasRole('ORIENTADOR')")
    public String paginaValidacoes(Model model, Authentication authentication) {

        // Busca os estágios pendentes desse orientador específico e joga no Model
        String email = authentication.getName();
        List<EstagioResponse> estagios = estagioService.listarPendentes(email);
        model.addAttribute("estagios", estagios);

        // Histórico de decisões de cada estágio, indexado pelo id, para a página mostrar as rejeições anteriores
        Map<Long, List<ValidacaoEstagioResponse>> historicos = new HashMap<>();
        for (EstagioResponse estagio : estagios) {
            historicos.put(estagio.getId(), estagioService.listarHistoricoDoOrientador(estagio.getId(), email));
        }
        model.addAttribute("historicos", historicos);

        // Estágios ATIVO do orientador, com as horas e o aviso de prazo de cada aluno
        model.addAttribute("estagiosAtivos", estagioService.listarAtivosDoOrientador(email));
        return "orientador/validacoes";
    }

    // rotas da secretaria

    // Dashboard da secretaria
    @GetMapping("/secretaria/dashboard")
    @PreAuthorize("hasRole('SECRETARIA')")
    public String dashboardSecretaria() {
        return "secretaria/dashboard";
    }

    // Página de envio de convite
    @GetMapping("/secretaria/enviar-convite")
    @PreAuthorize("hasRole('SECRETARIA')")
    public String enviarConvite() {
        return "secretaria/enviar-convite";
    }

    // Página com os convites enviados pela secretaria logada
    @GetMapping("/secretaria/convites")
    @PreAuthorize("hasRole('SECRETARIA')")
    public String listarConvites(Model model, Authentication authentication) {

        /*
           Busca só os convites que foram enviados pela própria secretaria,
           por isso filtra pelo e-mail do remetente que veio do token
        */
        String email = authentication.getName();
        model.addAttribute("convites", conviteService.listarEnviados(email));
        return "secretaria/convites";
    }
}
