package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tcc.ges.aprovamais.dto.PreCadastroResponse;
import tcc.ges.aprovamais.service.PreCadastroService;

import java.util.List;

// Consulta do pré-cadastro pela secretaria, para escolher quem convidar
@Tag(name = "Pré-cadastro na secretaria", description = "Pessoas autorizadas pelo coordenador que ainda não concluíram o cadastro")
@RestController
@RequestMapping("/api/v1/secretaria/pre-cadastros")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SECRETARIA')")
public class SecretariaPreCadastroController {

    private final PreCadastroService preCadastroService;

    @Operation(summary = "Listar os pré-cadastrados aguardando convite ou com convite pendente")
    @GetMapping
    public List<PreCadastroResponse> listar() {
        return preCadastroService.listarParaConvite();
    }
}
