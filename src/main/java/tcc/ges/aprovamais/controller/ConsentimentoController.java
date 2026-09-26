package tcc.ges.aprovamais.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tcc.ges.aprovamais.service.ConsentimentoService;

@RestController
@RequestMapping("/api/v1/consentimento")
@RequiredArgsConstructor
public class ConsentimentoController {

    private final ConsentimentoService consentimentoService;

    @PostMapping("/aceitar")
    public ResponseEntity<Void> aceitar(Authentication authentication,
                                        HttpServletRequest request) {
        consentimentoService.registrarAceite(authentication.getName(), request.getRemoteAddr());
        return ResponseEntity.ok().build();
    }
}