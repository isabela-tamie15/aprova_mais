package tcc.ges.aprovamais.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.PrimeiroAcessoRequest;
import tcc.ges.aprovamais.entity.Convite;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.UsuarioRepository;
import tcc.ges.aprovamais.service.ConviteService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Controller
@RequestMapping("/primeiro-acesso")
@RequiredArgsConstructor
public class PrimeiroAcessoController {

    private final ConviteService conviteService;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String VERSAO_TERMOS = "1.0";

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
    public String aceitar(@RequestParam String token,
                          @Valid @ModelAttribute PrimeiroAcessoRequest request,
                          Model model) {
        try {
            Convite convite = conviteService.validarToken(token);

            Usuario usuario = usuarioRepository.findByEmail(convite.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

            usuario.setSenhaHash(passwordEncoder.encode(request.getSenha()));
            usuario.setConsentimentoDado(true);
            usuario.setDataConsentimento(OffsetDateTime.now(ZoneOffset.UTC));
            usuario.setVersaoConsentimento(VERSAO_TERMOS);
            usuario.setPrimeiroAcesso(false);

            usuarioRepository.save(usuario);

            conviteService.aceitarConvite(token);

            return "redirect:/login?cadastro=concluido";

        } catch (Exception e) {
            model.addAttribute("erro", e.getMessage());
            model.addAttribute("token", token);
            return "primeiro-acesso";
        }
    }

    @PostMapping("/recusar")
    public String recusar(@RequestParam String token, Model model) {
        try {
            conviteService.recusarConvite(token);
            return "redirect:/login?convite=recusado";
        } catch (Exception e) {
            model.addAttribute("erro", e.getMessage());
            model.addAttribute("token", token);
            return "primeiro-acesso";
        }
    }
}