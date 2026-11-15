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
        } catch (Exception excecao) {
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
        try {
            // Passa o token, a senha escolhida e o IP para concluir o cadastro
            primeiroAcessoService.concluirCadastro(
                    token,
                    request.getSenha(),
                    httpRequest.getRemoteAddr()
            );
            return ResponseEntity.ok().build();
        } catch (Exception excecao) {
            // Se algo der errado, devolve 400 para o frontend sem expor o motivo
            return ResponseEntity.badRequest().build();
        }
    }

    // Esse é o endpoint que o usuário usa para recusar o convite e não criar a conta
    @PostMapping("/recusar")
    public String recusar(@RequestParam String token,
                          HttpServletRequest httpRequest,
                          Model model) {
        try {
            primeiroAcessoService.recusarCadastro(token, httpRequest.getRemoteAddr());
            return "redirect:/login?convite=recusado";
        } catch (Exception excecao) {
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