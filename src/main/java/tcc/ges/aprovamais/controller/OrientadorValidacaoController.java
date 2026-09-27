package tcc.ges.aprovamais.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.dto.RejeicaoRequest;
import tcc.ges.aprovamais.service.EstagioService;

import java.util.List;

// Esse controller é quem recebe as requisições do orientador pra validar os estágios dos alunos
@RestController
@RequestMapping("/api/v1/orientador/validacoes")
@RequiredArgsConstructor
public class OrientadorValidacaoController {

    private final EstagioService estagioService;

    // Esse é o endpoint que lista os estágios pendentes de validação do orientador logado
    @GetMapping
    public ResponseEntity<List<EstagioResponse>> listarPendentes(Authentication authentication) {

        /*
           E-mail vem do token, então cada orientador só vê os estágios
           que estão vinculados a ele mesmo
        */
        return ResponseEntity.ok(estagioService.listarPendentes(authentication.getName()));
    }

    // Esse é o endpoint que o orientador usa pra aprovar um estágio pendente
    @PostMapping("/{id}/aprovar")
    public ResponseEntity<EstagioResponse> aprovar(@PathVariable Long id,
                                                   Authentication authentication,
                                                   HttpServletRequest httpRequest) {

        /*
           O id do estágio vem pela URL e o e-mail vem do token. Os dois
           juntos vão pro service, que confirma se aquele estágio realmente
           pertence a esse orientador antes de aprovar
        */
        String email = authentication.getName();
        return ResponseEntity.ok(estagioService.aprovar(id, email, httpRequest.getRemoteAddr()));
    }

    // Esse é o endpoint que o orientador usa pra rejeitar um estágio, exige justificativa
    @PostMapping("/{id}/rejeitar")
    public ResponseEntity<EstagioResponse> rejeitar(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody RejeicaoRequest request,
            HttpServletRequest httpRequest) {

        /*
           Mesma ideia do aprovar, mas aqui vem uma justificativa obrigatória
           no corpo. Ela fica registrada pro aluno entender por que o
           estágio foi recusado
        */
        String email = authentication.getName();
        return ResponseEntity.ok(estagioService.rejeitar(
                id, email, request.getJustificativa(), httpRequest.getRemoteAddr()));
    }
}