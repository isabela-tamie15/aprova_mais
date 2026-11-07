package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tcc.ges.aprovamais.dto.VisaoAlunoResponse;
import tcc.ges.aprovamais.service.VisaoAlunoService;

// Visão e cada aluno do orientador
@Tag(name = "Acompanhamento do aluno", description = "Tarefas, versões, feedback e progresso de um aluno do orientador")
@RestController
@RequestMapping("/api/v1/orientador/estagios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ORIENTADOR')")
public class OrientadorAcompanhamentoController {

    private final VisaoAlunoService visaoAlunoService;

    @Operation(summary = "Consultar a visão consolidada do aluno (RF37)")
    @GetMapping("/{estagioId}/visao")
    public VisaoAlunoResponse visao(@PathVariable Long estagioId, Authentication authentication) {
        return visaoAlunoService.montar(authentication.getName(), estagioId);
    }
}
