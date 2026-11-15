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

// Esse controller é quem serve o fluxo de primeiro acesso, onde o convidado cria a senha pelo link do convite
@Controller
@RequestMapping("/primeiro-acesso")
@RequiredArgsConstructor
public class PrimeiroAcessoController {

    private final PrimeiroAcessoService primeiroAcessoService;
    private final ConviteService conviteService;

    // Esse é o endpoint que abre a página de primeiro acesso quando o usuário clica no link do convite
    @GetMapping
    public String exibirPrimeiroAcesso(@RequestParam String token, Model model) {
        try {
            /*
               Valida o token do convite e, se estiver tudo certo, coloca o
               token e o e-mail no Model para a página poder usar. Se o token
               estiver inválido ou expirado, cai no catch e mostra a página
               de erro com a mensagem da exceção
            */
            Convite convite = conviteService.validarToken(token);
            model.addAttribute("token", token);
            model.addAttribute("email", convite.getEmail());
            return "primeiro-acesso";
        } catch (ResourceNotFoundException | RegraNegocioException excecao) {
            // Só as falhas do convite (inexistente, usado ou expirado) viram a página de erro com a mensagem (AUD-18)
            model.addAttribute("erro", excecao.getMessage());
            return "primeiro-acesso-erro";
        }
    }

    // Esse é o endpoint que conclui o cadastro quando o usuário define a senha
    @PostMapping("/aceitar")
    @ResponseBody
    public ResponseEntity<Void> aceitar(@RequestParam String token,
                                        @Valid @RequestBody PrimeiroAcessoRequest request,
                                        HttpServletRequest httpRequest) {
        /*
           Passa o token, a senha escolhida e o IP para concluir o cadastro. Sem try aqui:
           convite inválido volta 404, usado ou expirado volta 409, os dois com a mensagem
           para a tela, e um erro inesperado vira 500 com a mensagem genérica do
           GlobalExceptionHandler, sem esconder a falha num 400 (AUD-18)
        */
        primeiroAcessoService.concluirCadastro(
                token,
                request.getSenha(),
                httpRequest.getRemoteAddr()
        );
        return ResponseEntity.ok().build();
    }

    // Esse é o endpoint que o usuário usa para recusar o convite e não criar a conta
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