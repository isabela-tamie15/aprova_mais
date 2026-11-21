package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.ConviteRequest;
import tcc.ges.aprovamais.dto.ConviteResponse;
import tcc.ges.aprovamais.service.ConviteService;

import java.util.List;

// Envio e consulta dos convites de acesso
@Tag(name = "Convites", description = "Envio de convites pela hierarquia de perfis (RF03)")
@RestController
@RequestMapping("/api/v1/convites")
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR', 'SECRETARIA')")
@RequiredArgsConstructor
public class ConviteController {

    private final ConviteService conviteService;

    // Envia o convite por e-mail para o perfil de destino
    @PostMapping
    public ResponseEntity<Void> enviarConvite(
            @Valid @RequestBody ConviteRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        /*
           O e-mail de quem envia vem do JWT, nunca do corpo. O envio do e-mail pelo Resend é assíncrono,
           então o 200 diz que o convite foi gravado, e não que o e-mail foi entregue
        */
        conviteService.enviarConvite(
                request.getEmailDestino(),
                request.getPerfil(),
                request.getNome(),
                authentication.getName(),
                httpRequest.getRemoteAddr()
        );

        return ResponseEntity.ok().build();
    }

    // Lista os convites enviados pelo usuário logado
    @GetMapping
    public ResponseEntity<List<ConviteResponse>> listarEnviados(Authentication authentication) {
        return ResponseEntity.ok(conviteService.listarEnviados(authentication.getName()));
    }
}
