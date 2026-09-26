package tcc.ges.aprovamais.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.ConviteRequest;
import tcc.ges.aprovamais.service.ConviteService;

// Esse controller é quem recebe as requisições de convite, usado pela secretaria pra liberar acesso
@RestController
@RequestMapping("/api/v1/convites")
@RequiredArgsConstructor
public class ConviteController {

    private final ConviteService conviteService;

    // Esse é o endpoint que a secretaria usa pra enviar um convite pra alguém entrar no sistema
    @PostMapping
    @PreAuthorize("hasRole('SECRETARIA')")
    public ResponseEntity<Void> enviarConvite(
            @Valid @RequestBody ConviteRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        /*
           Passa o e-mail de destino e o perfil vindos do corpo da requisição,
           o e-mail de quem está enviando (usuário autenticado pelo JWT) e o IP de origem para a auditoria.
           Os dois null são curso e turma: o formulário da secretaria ainda não permite escolhê-los,
           e o vínculo automático de curso e turma no aceite do convite está previsto.
           O e-mail é enviado de forma assíncrona pelo Resend, então o 200 indica que o convite foi gravado,
           não que o e-mail foi entregue
        */
        conviteService.enviarConvite(
                request.getEmailDestino(),
                request.getPerfil(),
                authentication.getName(),
                null,
                null,
                httpRequest.getRemoteAddr()
        );

        return ResponseEntity.ok().build();
    }
}
