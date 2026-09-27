package tcc.ges.aprovamais.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tcc.ges.aprovamais.service.ConsentimentoService;

// Esse controller é quem recebe o aceite dos termos de consentimento do usuário logado
@RestController
@RequestMapping("/api/v1/consentimento")
@RequiredArgsConstructor
public class ConsentimentoController {

    private final ConsentimentoService consentimentoService;

    // Esse é o endpoint que registra o aceite dos termos, junto com o IP de quem aceitou
    @PostMapping("/aceitar")
    public ResponseEntity<Void> aceitar(Authentication authentication,
                                        HttpServletRequest request) {

        /*
           E-mail vem do token e o IP da requisição. Os dois são guardados
           na auditoria pra servir de comprovação de que o usuário aceitou
           os termos naquele momento
        */
        consentimentoService.registrarAceite(authentication.getName(), request.getRemoteAddr());
        return ResponseEntity.ok().build();
    }
}