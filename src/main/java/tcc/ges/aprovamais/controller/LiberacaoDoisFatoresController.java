package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tcc.ges.aprovamais.dto.PedidoLiberacaoDoisFatoresRequest;
import tcc.ges.aprovamais.dto.PedidoLiberacaoDoisFatoresResponse;
import tcc.ges.aprovamais.dto.RejeicaoRequest;
import tcc.ges.aprovamais.service.LiberacaoDoisFatoresService;

import java.util.List;

@Tag(name = "Liberação do 2FA", description = "Pedidos de quem perdeu o acesso ao aplicativo autenticador")
@RestController
@RequiredArgsConstructor
public class LiberacaoDoisFatoresController {

    private final LiberacaoDoisFatoresService liberacaoService;

    @Operation(summary = "Pedir a liberação do 2FA na etapa do código do login")
    @PostMapping("/api/v1/auth/2fa/liberacao")
    public ResponseEntity<Void> solicitar(@Valid @RequestBody PedidoLiberacaoDoisFatoresRequest requisicao,
                                          HttpServletRequest request) {
        liberacaoService.solicitar(requisicao.getToken(), requisicao.getMotivo(), request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Listar os pedidos de liberação do 2FA em análise")
    @GetMapping({"/api/v1/admin/liberacoes-2fa", "/api/v1/coordenador/liberacoes-2fa"})
    public List<PedidoLiberacaoDoisFatoresResponse> listar(Authentication authentication) {
        return liberacaoService.listarPendentes(authentication.getName());
    }

    @Operation(summary = "Aprovar um pedido e desativar o 2FA da conta")
    @PostMapping({"/api/v1/admin/liberacoes-2fa/{id}/aprovar", "/api/v1/coordenador/liberacoes-2fa/{id}/aprovar"})
    public ResponseEntity<Void> aprovar(@PathVariable Long id, Authentication authentication, HttpServletRequest request) {
        liberacaoService.aprovar(id, authentication.getName(), request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Recusar um pedido com justificativa")
    @PostMapping({"/api/v1/admin/liberacoes-2fa/{id}/recusar", "/api/v1/coordenador/liberacoes-2fa/{id}/recusar"})
    public ResponseEntity<Void> recusar(@PathVariable Long id, @Valid @RequestBody RejeicaoRequest requisicao,
                                        Authentication authentication, HttpServletRequest request) {
        liberacaoService.recusar(id, requisicao.getJustificativa(), authentication.getName(), request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
