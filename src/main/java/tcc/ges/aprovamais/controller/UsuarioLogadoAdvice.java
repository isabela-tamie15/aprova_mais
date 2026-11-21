package tcc.ges.aprovamais.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.repository.UsuarioRepository;


@ControllerAdvice(assignableTypes = {
        PaginaController.class,
        AdministradorPaginaController.class,
        CoordenadorPaginaController.class,
        OrientadorPaginaController.class
})
@RequiredArgsConstructor
public class UsuarioLogadoAdvice {

    private final UsuarioRepository usuarioRepository;

    @ModelAttribute("usuarioLogado")
    public Usuario usuarioLogado(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return usuarioRepository.findByEmail(authentication.getName()).orElse(null);
    }
}
