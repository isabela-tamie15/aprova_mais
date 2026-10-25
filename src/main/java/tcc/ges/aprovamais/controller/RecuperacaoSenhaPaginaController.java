package tcc.ges.aprovamais.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tcc.ges.aprovamais.service.RecuperacaoSenhaService;

// Esse controller serve as duas páginas públicas da recuperação de senha
@Controller
@RequiredArgsConstructor
public class RecuperacaoSenhaPaginaController {

    private final RecuperacaoSenhaService recuperacaoSenhaService;

    // Esse é o endpoint que serve a página onde o usuário informa o e-mail
    @GetMapping("/recuperar-senha")
    public String recuperarSenha() {
        return "recuperar-senha";
    }

    /*
       Esse é o endpoint que serve a página do link do e-mail. Confere o token antes,
       assim um link usado ou vencido já mostra o aviso, em vez de deixar a pessoa
       digitar a senha nova e só descobrir no fim
    */
    @GetMapping("/redefinir-senha")
    public String redefinirSenha(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("tokenValido", recuperacaoSenhaService.tokenValido(token));
        return "redefinir-senha";
    }
}
