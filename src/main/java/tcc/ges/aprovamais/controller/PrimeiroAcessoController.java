package tcc.ges.aprovamais.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.PrimeiroAcessoRequest;
import tcc.ges.aprovamais.entity.Convite;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.service.ConviteService;
import tcc.ges.aprovamais.service.PrimeiroAcessoService;

// Primeiro acesso, em que o convidado cria a senha pelo link do convite
@Controller
@RequestMapping("/primeiro-acesso")
@RequiredArgsConstructor
public class PrimeiroAcessoController {

    private final PrimeiroAcessoService primeiroAcessoService;
    private final ConviteService conviteService;

    // Abre a página de primeiro acesso pelo link do convite
    @GetMapping
    public String exibirPrimeiroAcesso(@RequestParam String token, Model model) {
        try {
            // Com o token válido, a página recebe o token e o e-mail. Token inválido ou expirado mostra a página de erro com a mensagem
            Convite convite = conviteService.validarToken(token);
            model.addAttribute("token", token);
            model.addAttribute("email", convite.getEmail());
            return "primeiro-acesso";
        } catch (ResourceNotFoundException | RegraNegocioException excecao) {
            // Só as falhas do convite, inexistente, usado ou expirado, viram a página de erro
            model.addAttribute("erro", excecao.getMessage());
            return "primeiro-acesso-erro";
        }
    }

    // Conclui o cadastro com a senha definida pelo usuário
    @PostMapping("/aceitar")
    @ResponseBody
    public ResponseEntity<Void> aceitar(@RequestParam String token,
                                        @Valid @RequestBody PrimeiroAcessoRequest request,
                                        HttpServletRequest httpRequest) {
        // Convite inválido volta 404 e convite usado ou expirado volta 409, os dois com a mensagem para a tela
        primeiroAcessoService.concluirCadastro(
                token,
                request.getSenha(),
                httpRequest.getRemoteAddr()
        );
        return ResponseEntity.ok().build();
    }

    // Recusa o convite sem criar a conta
    @PostMapping("/recusar")
    public String recusar(@RequestParam String token,
                          HttpServletRequest httpRequest,
                          Model model) {
        try {
            primeiroAcessoService.recusarCadastro(token, httpRequest.getRemoteAddr());
            return "redirect:/login?convite=recusado";
        } catch (ResourceNotFoundException | RegraNegocioException excecao) {
            /*
               Se falhar, volta para a página de primeiro acesso com a mensagem
               de erro em vez de redirecionar para o login
            */
            model.addAttribute("erro", excecao.getMessage());
            model.addAttribute("token", token);
            return "primeiro-acesso";
        }
    }
}