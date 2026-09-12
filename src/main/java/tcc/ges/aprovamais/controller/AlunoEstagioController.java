package tcc.ges.aprovamais.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.EstagioCadastroRequest;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.dto.TipoEstagioResponse;
import tcc.ges.aprovamais.service.EstagioService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/aluno/estagio")
@RequiredArgsConstructor
public class AlunoEstagioController {

    private final EstagioService estagioService;

    @GetMapping("/tipos")
    public ResponseEntity<List<TipoEstagioResponse>> listarTiposEstagio() {
        List<TipoEstagioResponse> tipos = estagioService.listarTiposEstagioDisponiveis();
        return ResponseEntity.ok(tipos);
    }

    @GetMapping
    public ResponseEntity<EstagioResponse> buscarMeuEstagio(Authentication authentication) {
        String email = authentication.getName();
        Optional<EstagioResponse> estagioEncontrado = estagioService.buscarEstagioDoAluno(email);

        if (estagioEncontrado.isPresent()) {
            EstagioResponse resposta = estagioEncontrado.get();
            return ResponseEntity.ok(resposta);
        } else {
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping
    public ResponseEntity<EstagioResponse> cadastrarOuAtualizar(
            Authentication authentication,
            @Valid @RequestBody EstagioCadastroRequest request) {

        String email = authentication.getName();
        EstagioResponse resposta = estagioService.cadastrarOuAtualizarEstagio(email, request);
        return ResponseEntity.ok(resposta);
    }
}