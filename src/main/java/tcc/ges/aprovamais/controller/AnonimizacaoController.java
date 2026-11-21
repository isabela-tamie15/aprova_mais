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
import tcc.ges.aprovamais.dto.SolicitacaoTitularResponse;
import tcc.ges.aprovamais.service.AnonimizacaoService;

// Pedidos de LGPD do usuário logado
@Tag(name = "LGPD", description = "Solicitações do titular e anonimização da conta")
@RestController
@RequestMapping("/api/v1/lgpd")
@RequiredArgsConstructor
public class AnonimizacaoController {

    private final AnonimizacaoService anonimizacaoService;

    /*
       A Lei 11.788/2008 obriga a guardar os registros do estágio, então excluir aqui significa
       anonimizar. O AnonimizacaoService troca nome, e-mail e RGM por valores neutros
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

    // Pedido de acesso aos próprios dados
    @PostMapping("/solicitar-acesso")
    public ResponseEntity<SolicitacaoTitularResponse> solicitarAcesso(
            Authentication authentication,
            HttpServletRequest request) {

        // Registra o pedido na auditoria e devolve o protocolo e o prazo da resposta. Os dados são entregues pela secretaria
        return ResponseEntity.ok(anonimizacaoService.registrarSolicitacao(authentication.getName(),
                "SOLICITACAO_ACESSO_DADOS",
                "Titular solicitou acesso aos seus dados pessoais",
                request.getRemoteAddr()));
    }

    // Pedido de correção dos próprios dados
    @PostMapping("/solicitar-correcao")
    public ResponseEntity<SolicitacaoTitularResponse> solicitarCorrecao(
            Authentication authentication,
            HttpServletRequest request) {

        // Mesma ideia do acesso, só registra a solicitação para a auditoria
        return ResponseEntity.ok(anonimizacaoService.registrarSolicitacao(authentication.getName(),
                "SOLICITACAO_CORRECAO_DADOS",
                "Titular solicitou correção dos seus dados pessoais",
                request.getRemoteAddr()));
    }

}