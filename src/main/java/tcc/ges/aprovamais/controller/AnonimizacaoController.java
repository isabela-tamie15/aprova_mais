package tcc.ges.aprovamais.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.UsuarioRepository;
import tcc.ges.aprovamais.service.AnonimizacaoService;
import tcc.ges.aprovamais.service.AuditoriaService;

@RestController
@RequestMapping("/api/v1/lgpd")
@RequiredArgsConstructor
public class AnonimizacaoController {

    private final AnonimizacaoService anonimizacaoService;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    @DeleteMapping("/meus-dados")
    public ResponseEntity<Void> solicitarExclusao(
            Authentication authentication,
            HttpServletRequest request) {
        anonimizacaoService.anonimizar(
                authentication.getName(),
                request.getRemoteAddr()
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/solicitar-acesso")
    public ResponseEntity<Void> solicitarAcesso(
            Authentication authentication,
            HttpServletRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        auditoriaService.registrar(usuario,
                "SOLICITACAO_ACESSO_DADOS",
                "Titular solicitou acesso aos seus dados pessoais",
                request.getRemoteAddr(), true);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/solicitar-correcao")
    public ResponseEntity<Void> solicitarCorrecao(
            Authentication authentication,
            HttpServletRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        auditoriaService.registrar(usuario,
                "SOLICITACAO_CORRECAO_DADOS",
                "Titular solicitou correção dos seus dados pessoais",
                request.getRemoteAddr(), true);
        return ResponseEntity.ok().build();
    }
}