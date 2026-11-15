package tcc.ges.aprovamais.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tcc.ges.aprovamais.service.RecuperacaoSenhaService;

// Páginas públicas da recuperação de senha
@Controller
@RequiredArgsConstructor
public class RecuperacaoSenhaPaginaController {

    private final RecuperacaoSenhaService recuperacaoSenhaService;

    // Página em que o usuário informa o e-mail
    @GetMapping("/recuperar-senha")
    public String recuperarSenha() {
        return "recuperar-senha";
    }

    // Página do link do e-mail. O token é conferido antes, para um link usado ou vencido já mostrar o aviso
    @GetMapping("/redefinir-senha")
    public String redefinirSenha(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("tokenValido", recuperacaoSenhaService.tokenValido(token));
        return "redefinir-senha";
    }
}
