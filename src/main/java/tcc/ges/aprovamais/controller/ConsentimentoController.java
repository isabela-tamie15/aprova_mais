package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tcc.ges.aprovamais.service.ConsentimentoService;

// Aceite dos termos pelo usuário logado
@Tag(name = "LGPD", description = "Aceite dos Termos de Uso e da Política de Privacidade")
@RestController
@RequestMapping("/api/v1/consentimento")
@RequiredArgsConstructor
public class ConsentimentoController {

    private final ConsentimentoService consentimentoService;

    // Registra o aceite dos termos com o IP de quem aceitou
    @PostMapping("/aceitar")
    public ResponseEntity<Void> aceitar(Authentication authentication,
                                        HttpServletRequest request) {

        // O e-mail do token e o IP ficam na auditoria como comprovação do aceite naquele momento
        consentimentoService.registrarAceite(authentication.getName(), request.getRemoteAddr());
        return ResponseEntity.ok().build();
    }
}