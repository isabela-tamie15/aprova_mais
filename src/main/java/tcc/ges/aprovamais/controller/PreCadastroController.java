package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.PreCadastroRequest;
import tcc.ges.aprovamais.dto.PreCadastroResponse;
import tcc.ges.aprovamais.service.PreCadastroService;

import java.util.List;

// Lista de pré-cadastro mantida pelo coordenador
@Tag(name = "Pré-cadastro", description = "Pessoas autorizadas pelo coordenador a receber convite")
@RestController
@RequestMapping("/api/v1/coordenador/pre-cadastros")
@RequiredArgsConstructor
public class PreCadastroController {

    private final PreCadastroService preCadastroService;

    // Lista os pré-cadastros do coordenador logado
    @GetMapping
    public ResponseEntity<List<PreCadastroResponse>> listar(Authentication authentication) {
        return ResponseEntity.ok(preCadastroService.listarDoCoordenador(authentication.getName()));
    }

    // Inclui uma pessoa no pré-cadastro
    @PostMapping
    public ResponseEntity<PreCadastroResponse> cadastrar(@Valid @RequestBody PreCadastroRequest request,
                                                         Authentication authentication,
                                                         HttpServletRequest httpRequest) {
        PreCadastroResponse criado = preCadastroService.cadastrar(
                authentication.getName(), request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    // Altera um pré-cadastro do coordenador logado
    @PutMapping("/{id}")
    public ResponseEntity<PreCadastroResponse> atualizar(@PathVariable Long id,
                                                         @Valid @RequestBody PreCadastroRequest request,
                                                         Authentication authentication,
                                                         HttpServletRequest httpRequest) {
        return ResponseEntity.ok(preCadastroService.atualizar(
                id, authentication.getName(), request, httpRequest.getRemoteAddr()));
    }
}
