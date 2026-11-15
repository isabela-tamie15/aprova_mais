package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tcc.ges.aprovamais.dto.AnonimizacaoRequest;
import tcc.ges.aprovamais.service.AnonimizacaoService;

// Esse controller é quem recebe as solicitações de LGPD do usuário logado
@Tag(name = "LGPD", description = "Solicitações do titular e anonimização da conta")
@RestController
@RequestMapping("/api/v1/lgpd")
@RequiredArgsConstructor
public class AnonimizacaoController {

    private final AnonimizacaoService anonimizacaoService;

    /*
       DELETE /api/v1/lgpd/meus-dados: o método DELETE já diz a ação (excluir) e a URL diz o quê
       (os meus dados). Como a Lei 11.788/2008 obriga a guardar os registros do estágio,
       excluir aqui significa anonimizar: o AnonimizacaoService troca nome, e-mail e RGM por valores neutros
    */
    @DeleteMapping("/meus-dados")
    public ResponseEntity<Void> solicitarExclusao(
            @Valid @RequestBody AnonimizacaoRequest requisicao,
            Authentication authentication,
            HttpServletRequest request) {

        /*
           O e-mail vem do token e o IP da requisição, assim o usuário
           só consegue anonimizar a própria conta, nunca a de outro
        */
        anonimizacaoService.anonimizar(
                authentication.getName(),
                requisicao.getSenha(),
                requisicao.getCodigo(),
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
           Aqui nós não devolvemos os dados ainda, só registra a solicitação
           na auditoria para ficar rastreável. Quem entrega os dados em si é
           outro fluxo, mais controlado
        */
        anonimizacaoService.registrarSolicitacao(authentication.getName(),
                "SOLICITACAO_ACESSO_DADOS",
                "Titular solicitou acesso aos seus dados pessoais",
                request.getRemoteAddr());
        return ResponseEntity.ok().build();
    }

    // Esse é o endpoint onde o usuário solicita correção dos próprios dados
    @PostMapping("/solicitar-correcao")
    public ResponseEntity<Void> solicitarCorrecao(
            Authentication authentication,
            HttpServletRequest request) {

        // Mesma ideia do acesso, só registra a solicitação para a auditoria
        anonimizacaoService.registrarSolicitacao(authentication.getName(),
                "SOLICITACAO_CORRECAO_DADOS",
                "Titular solicitou correção dos seus dados pessoais",
                request.getRemoteAddr());
        return ResponseEntity.ok().build();
    }

}