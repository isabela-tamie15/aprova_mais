package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tcc.ges.aprovamais.dto.EncerramentoEstagioRequest;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.dto.VisaoAlunoResponse;
import tcc.ges.aprovamais.service.EncerramentoEstagioService;
import tcc.ges.aprovamais.service.VisaoAlunoService;

// Visão e cada aluno do orientador
@Tag(name = "Acompanhamento do aluno", description = "Tarefas, versões, feedback e progresso de um aluno do orientador")
@RestController
@RequestMapping("/api/v1/orientador/estagios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ORIENTADOR')")
public class OrientadorAcompanhamentoController {

    private final VisaoAlunoService visaoAlunoService;
    private final EncerramentoEstagioService encerramentoEstagioService;

    @Operation(summary = "Consultar a visão consolidada do aluno (RF37)")
    @GetMapping("/{estagioId}/visao")
    public VisaoAlunoResponse visao(@PathVariable Long estagioId, Authentication authentication) {
        return visaoAlunoService.montar(authentication.getName(), estagioId);
    }

    @Operation(summary = "Concluir o estágio do aluno")
    @PostMapping("/{estagioId}/concluir")
    public EstagioResponse concluir(@PathVariable Long estagioId, Authentication authentication,
                                    HttpServletRequest httpRequest) {
        return encerramentoEstagioService.concluir(estagioId, authentication.getName(), httpRequest.getRemoteAddr());
    }

    @Operation(summary = "Encerrar o estágio do aluno por reprovação")
    @PostMapping("/{estagioId}/reprovar")
    public EstagioResponse reprovar(@PathVariable Long estagioId, @Valid @RequestBody EncerramentoEstagioRequest request,
                                    Authentication authentication, HttpServletRequest httpRequest) {
        return encerramentoEstagioService.reprovar(estagioId, authentication.getName(),
                request.getObservacao(), httpRequest.getRemoteAddr());
    }
}
