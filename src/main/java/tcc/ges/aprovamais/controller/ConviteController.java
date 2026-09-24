package tcc.ges.aprovamais.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.ConviteRequest;
import tcc.ges.aprovamais.service.ConviteService;

@RestController
@RequestMapping("/api/v1/convites")
@RequiredArgsConstructor
public class ConviteController {

    private final ConviteService conviteService;

    @PostMapping
    @PreAuthorize("hasRole('SECRETARIA')")
    public ResponseEntity<Void> enviarConvite(
            @Valid @RequestBody ConviteRequest request,
            Authentication authentication) {

        conviteService.enviarConvite(
                request.getEmailDestino(),
                request.getPerfil(),
                authentication.getName(),
                null,
                null
        );

        return ResponseEntity.ok().build();
    }
}
