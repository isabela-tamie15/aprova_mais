package tcc.ges.aprovamais.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.UsuarioRepository;
import tcc.ges.aprovamais.service.AuditoriaService;

@RestController
@RequestMapping("/api/v1/consentimento")
@RequiredArgsConstructor
public class ConsentimentoController {

    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    @PostMapping("/aceitar")
    public ResponseEntity<Void> aceitar(Authentication authentication,
                                        HttpServletRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        usuario.setConsentimentoDado(true);
        usuarioRepository.save(usuario);

        auditoriaService.registrar(
                usuario,
                "CONSENTIMENTO_ACEITO",
                "Usuário aceitou os termos de uso e política de privacidade",
                request.getRemoteAddr(),
                true
        );

        return ResponseEntity.ok().build();
    }
}