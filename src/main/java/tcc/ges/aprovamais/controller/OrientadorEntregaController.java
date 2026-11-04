package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.AvaliacaoEntregaRequest;
import tcc.ges.aprovamais.dto.EntregaDetalheResponse;
import tcc.ges.aprovamais.dto.PainelCorrecaoResponse;
import tcc.ges.aprovamais.entity.enums.StatusEntrega;
import tcc.ges.aprovamais.service.CorrecaoService;
import tcc.ges.aprovamais.service.EntregaService;

import java.time.LocalDate;

// Painel de correção e avaliação das entregas pelo orientador
@Tag(name = "Correção das entregas", description = "Painel do orientador, avaliação com checklist e download dos arquivos")
@RestController
@RequestMapping("/api/v1/orientador/entregas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ORIENTADOR')")
public class OrientadorEntregaController {

    private final CorrecaoService correcaoService;
    private final EntregaService entregaService;

    @Operation(summary = "Listar as entregas no painel de correção, com filtros (RF35)")
    @GetMapping
    public PainelCorrecaoResponse listar(@RequestParam(required = false) Long alunoId,
                                         @RequestParam(required = false) Long tarefaId,
                                         @RequestParam(required = false) StatusEntrega status,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                         @RequestParam(defaultValue = "0") int pagina,
                                         @RequestParam(defaultValue = "10") int tamanho,
                                         Authentication authentication) {


        return correcaoService.listar(authentication.getName(), new CorrecaoService.FiltroPainel(alunoId, tarefaId, status, de, ate, pagina, tamanho));
    }

    @Operation(summary = "Abrir a entrega para avaliação; ENVIADA passa a EM_REVISAO (RF36)")
    @GetMapping("/{id}")
    public EntregaDetalheResponse abrir(@PathVariable Long id, Authentication authentication) {

        return correcaoService.abrir(authentication.getName(), id);

    }

    @Operation(summary = "Aprovar ou devolver a entrega, com o checklist (RF36, RF40)")
    @PostMapping("/{id}/avaliacao")
    public EntregaDetalheResponse avaliar(@PathVariable Long id,
                                          @Valid @RequestBody AvaliacaoEntregaRequest request,
                                          Authentication authentication,
                                          HttpServletRequest httpRequest) {
        return correcaoService.avaliar(authentication.getName(), id, request, httpRequest.getRemoteAddr());
    }

    @Operation(summary = "Baixar o arquivo de uma versão da entrega")
    @GetMapping("/versoes/{versaoId}/arquivo")
    public ResponseEntity<byte[]> baixar(@PathVariable Long versaoId, Authentication authentication) {
        return ArquivoDownload.resposta(entregaService.baixarDoOrientador(authentication.getName(), versaoId));
    }
}
