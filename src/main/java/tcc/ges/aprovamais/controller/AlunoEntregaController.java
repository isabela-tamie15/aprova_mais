package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import tcc.ges.aprovamais.dto.EntregaDetalheResponse;
import tcc.ges.aprovamais.dto.ExclusaoAnexoRequest;
import tcc.ges.aprovamais.dto.SolicitacaoResponse;
import tcc.ges.aprovamais.service.EntregaService;
import tcc.ges.aprovamais.service.SolicitacaoService;

import java.io.IOException;

// Entregas das tarefas da trilha pelo aluno
@Tag(name = "Entregas do aluno", description = "Envio de arquivos para as tarefas da trilha e histórico de versões")
@RestController
@RequestMapping("/api/v1/aluno")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ALUNO')")
public class AlunoEntregaController {

    private final EntregaService entregaService;
    private final SolicitacaoService solicitacaoService;

    // Envia um arquivo para a tarefa, e cada envio cria uma versão nova. O e-mail vem do token, então ninguém envia no lugar de outro aluno
    @Operation(summary = "Enviar a entrega de uma tarefa (RF33)")
    @PostMapping(value = "/tarefas/{tarefaId}/entregas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EntregaDetalheResponse> enviar(@PathVariable Long tarefaId,
                                                         @RequestPart("arquivo") MultipartFile arquivo,
                                                         @RequestParam(value = "comentario", required = false) String comentario,
                                                         Authentication authentication,
                                                         HttpServletRequest request) throws IOException {
        EntregaDetalheResponse resposta = entregaService.enviar(authentication.getName(), tarefaId,
                arquivo.getOriginalFilename(), arquivo.getBytes(), comentario, request.getRemoteAddr());
        return ResponseEntity.status(201).body(resposta);
    }

    @Operation(summary = "Consultar a entrega de uma tarefa com o histórico de versões (RF34)")
    @GetMapping("/tarefas/{tarefaId}/entrega")
    public EntregaDetalheResponse buscar(@PathVariable Long tarefaId, Authentication authentication) {
        return entregaService.buscarDoAluno(authentication.getName(), tarefaId);
    }

    @Operation(summary = "Baixar o arquivo de uma versão da entrega")
    @GetMapping("/entregas/versoes/{versaoId}/arquivo")
    public ResponseEntity<byte[]> baixar(@PathVariable Long versaoId, Authentication authentication,
                                         HttpServletRequest httpRequest) {
        return ArquivoDownload.resposta(entregaService.baixarDoAluno(
                authentication.getName(), versaoId, httpRequest.getRemoteAddr()));
    }

    @Operation(summary = "Cancelar o último envio antes de o orientador abrir a entrega")
    @DeleteMapping("/entregas/versoes/{versaoId}")
    public EntregaDetalheResponse cancelarEnvio(@PathVariable Long versaoId, Authentication authentication,
                                                HttpServletRequest httpRequest) {
        return entregaService.cancelarEnvio(authentication.getName(), versaoId, httpRequest.getRemoteAddr());
    }

    @Operation(summary = "Pedir a exclusão do anexo de uma versão")
    @PostMapping("/entregas/versoes/{versaoId}/exclusao")
    public ResponseEntity<SolicitacaoResponse> solicitarExclusao(@PathVariable Long versaoId,
                                                                 @Valid @RequestBody ExclusaoAnexoRequest request,
                                                                 Authentication authentication,
                                                                 HttpServletRequest httpRequest) {
        SolicitacaoResponse resposta = solicitacaoService.solicitarExclusaoAnexo(authentication.getName(), versaoId,
                request.getMotivo(), httpRequest.getRemoteAddr());
        return ResponseEntity.status(201).body(resposta);
    }
}
