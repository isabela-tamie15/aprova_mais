package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.ConfiguracaoRequest;
import tcc.ges.aprovamais.dto.ConfiguracaoResponse;
import tcc.ges.aprovamais.dto.CorrecaoDoisFatoresRequest;
import tcc.ges.aprovamais.dto.CorrecaoStatusEstagioRequest;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.service.ConfiguracaoService;
import tcc.ges.aprovamais.service.CorrecaoExcepcionalService;

import java.util.List;

// Configurações do sistema e correções excepcionais, só para o administrador
@Tag(name = "Administração", description = "Parâmetros do sistema e correções excepcionais com justificativa")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AdministracaoController {

    private final ConfiguracaoService configuracaoService;
    private final CorrecaoExcepcionalService correcaoExcepcionalService;

    @Operation(summary = "Listar os parâmetros do sistema")
    @GetMapping("/configuracoes")
    public List<ConfiguracaoResponse> listarConfiguracoes() {
        return configuracaoService.listar();
    }

    @Operation(summary = "Alterar um parâmetro do sistema")
    @PutMapping("/configuracoes/{parametro}")
    public ConfiguracaoResponse alterarConfiguracao(@PathVariable String parametro,
                                                    @Valid @RequestBody ConfiguracaoRequest request,
                                                    Authentication authentication, HttpServletRequest httpRequest) {
        return configuracaoService.alterar(parametro, request.getValor(), authentication.getName(),
                httpRequest.getRemoteAddr());
    }

    @Operation(summary = "Desativar o 2FA de um usuário")
    @PostMapping("/correcoes/dois-fatores")
    public ResponseEntity<Void> desativarDoisFatores(@Valid @RequestBody CorrecaoDoisFatoresRequest request,
                                                     Authentication authentication, HttpServletRequest httpRequest) {
        correcaoExcepcionalService.desativarDoisFatores(request.getEmail(), request.getJustificativa(),
                authentication.getName(), httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Consultar um estágio para correção")
    @GetMapping("/correcoes/estagios/{id}")
    public EstagioResponse buscarEstagio(@PathVariable Long id) {
        return correcaoExcepcionalService.buscarEstagio(id);
    }

    @Operation(summary = "Corrigir o status de um estágio")
    @PostMapping("/correcoes/estagios/{id}/status")
    public EstagioResponse corrigirStatusEstagio(@PathVariable Long id, @Valid @RequestBody CorrecaoStatusEstagioRequest request,
                                                 Authentication authentication, HttpServletRequest httpRequest) {
        return correcaoExcepcionalService.corrigirStatusEstagio(id, request.getStatus(), request.getMotivoEncerramento(),
                request.getJustificativa(), authentication.getName(), httpRequest.getRemoteAddr());
    }
}
