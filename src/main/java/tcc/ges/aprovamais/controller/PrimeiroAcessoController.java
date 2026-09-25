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

@Controller
@RequestMapping("/primeiro-acesso")
@RequiredArgsConstructor
public class PrimeiroAcessoController {

    private final PrimeiroAcessoService primeiroAcessoService;
    private final ConviteService conviteService;

    @GetMapping
    public String exibirPrimeiroAcesso(@RequestParam String token, Model model) {
        try {
            Convite convite = conviteService.validarToken(token);
            model.addAttribute("token", token);
            model.addAttribute("email", convite.getEmail());
            return "primeiro-acesso";
        } catch (Exception e) {
            model.addAttribute("erro", e.getMessage());
            return "primeiro-acesso-erro";
        }
    }

    @PostMapping("/aceitar")
    @ResponseBody
    public ResponseEntity<Void> aceitar(@RequestParam String token,
                                        @Valid @RequestBody PrimeiroAcessoRequest request,
                                        HttpServletRequest httpRequest) {
        try {
            primeiroAcessoService.concluirCadastro(
                    token,
                    request.getSenha(),
                    httpRequest.getRemoteAddr()
            );
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/recusar")
    public String recusar(@RequestParam String token,
                          HttpServletRequest httpRequest,
                          Model model) {
        try {
            primeiroAcessoService.recusarCadastro(token, httpRequest.getRemoteAddr());
            return "redirect:/login?convite=recusado";
        } catch (Exception e) {
            model.addAttribute("erro", e.getMessage());
            model.addAttribute("token", token);
            return "primeiro-acesso";
        }
    }
}