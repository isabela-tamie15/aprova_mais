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

// Esse controller é quem recebe as solicitações de LGPD do usuário logado
@RestController
@RequestMapping("/api/v1/lgpd")
@RequiredArgsConstructor
public class AnonimizacaoController {

    private final AnonimizacaoService anonimizacaoService;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    // Esse é o endpoint que o usuário usa pra pedir a exclusão dos próprios dados
    @DeleteMapping("/meus-dados")
    public ResponseEntity<Void> solicitarExclusao(
            Authentication authentication,
            HttpServletRequest request) {

        /*
           O e-mail vem do token e o IP da requisição, assim o usuário
           só consegue anonimizar a própria conta, nunca a de outro
        */
        anonimizacaoService.anonimizar(
                authentication.getName(),
                request.getRemoteAddr()
        );
        return ResponseEntity.ok().build();
    }

    // Esse é o endpoint onde o usuário solicita acesso aos próprios dados
    @PostMapping("/solicitar-acesso")
    public ResponseEntity<Void> solicitarAcesso(
            Authentication authentication,
            HttpServletRequest request) {

        /*
           Aqui a gente não devolve os dados ainda, só registra a solicitação
           na auditoria pra ficar rastreável. Quem entrega os dados em si é
           outro fluxo, mais controlado
        */
        Usuario usuario = usuarioRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        auditoriaService.registrar(usuario,
                "SOLICITACAO_ACESSO_DADOS",
                "Titular solicitou acesso aos seus dados pessoais",
                request.getRemoteAddr(), true);
        return ResponseEntity.ok().build();
    }

    // Esse é o endpoint onde o usuário solicita correção dos próprios dados
    @PostMapping("/solicitar-correcao")
    public ResponseEntity<Void> solicitarCorrecao(
            Authentication authentication,
            HttpServletRequest request) {

        // Mesma ideia do acesso, só registra a solicitação pra auditoria
        Usuario usuario = usuarioRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        auditoriaService.registrar(usuario,
                "SOLICITACAO_CORRECAO_DADOS",
                "Titular solicitou correção dos seus dados pessoais",
                request.getRemoteAddr(), true);
        return ResponseEntity.ok().build();
    }
}