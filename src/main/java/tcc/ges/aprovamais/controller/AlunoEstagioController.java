package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.EstagioCadastroRequest;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.dto.TipoEstagioResponse;
import tcc.ges.aprovamais.dto.ValidacaoEstagioResponse;
import tcc.ges.aprovamais.service.EstagioService;

import java.util.List;
import java.util.Optional;

// Esse controller é quem recebe as requisições do aluno relacionadas ao estágio
@Tag(name = "Estágio do aluno", description = "Cadastro, consulta e reenvio do estágio pelo aluno")
@RestController
@RequestMapping("/api/v1/aluno/estagio")
@RequiredArgsConstructor
public class AlunoEstagioController {

    private final EstagioService estagioService;

    // Esse é o endpoint que devolve a lista de tipos de estágio que o aluno pode escolher, só os do curso dele
    @GetMapping("/tipos")
    public ResponseEntity<List<TipoEstagioResponse>> listarTiposEstagio(Authentication authentication) {
        List<TipoEstagioResponse> tipos = estagioService.listarTiposEstagioDisponiveis(authentication.getName());
        return ResponseEntity.ok(tipos);
    }

    // Esse é o endpoint que devolve o estágio atual do aluno logado
    @GetMapping
    public ResponseEntity<EstagioResponse> buscarMeuEstagio(Authentication authentication) {

        /*
           Pega o e-mail do token em vez de receber por parâmetro, assim o aluno
           não consegue passar o e-mail de outro e ver o estágio alheio
        */
        String email = authentication.getName();
        Optional<EstagioResponse> estagioEncontrado = estagioService.buscarEstagioDoAluno(email);

        /*
           Se achou, devolve com 200. Se não achou, devolve 204 No Content,
           que é a forma padrão de dizer que não tem nada pra retornar
        */
        if (estagioEncontrado.isPresent()) {
            EstagioResponse resposta = estagioEncontrado.get();
            return ResponseEntity.ok(resposta);
        } else {
            return ResponseEntity.noContent().build();
        }
    }

    // Esse é o endpoint que o aluno usa pra cadastrar um novo estágio ou atualizar o que já existe
    @PostMapping
    public ResponseEntity<EstagioResponse> cadastrarOuAtualizar(
            Authentication authentication,
            @Valid @RequestBody EstagioCadastroRequest request,
            HttpServletRequest httpRequest) {

        /*
           Mesma ideia do buscar, e-mail vem do token. O IP também vai pro
           service pra ficar registrado na auditoria
        */
        String email = authentication.getName();
        EstagioResponse resposta = estagioService.cadastrarOuAtualizarEstagio(
                email, request, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(resposta);
    }

    // Esse endpoint que devolve as aprovações e rejeições do estágio do aluno logado, da mais antiga pra mais recente
    @GetMapping("/historico")
    public ResponseEntity<List<ValidacaoEstagioResponse>> historico(Authentication authentication) {
        return ResponseEntity.ok(estagioService.listarHistoricoDoAluno(authentication.getName()));
    }

    // Esse é o endpoint que lista todos os estágios da matrícula do aluno, o atual e os já concluídos ou encerrados
    @GetMapping("/todos")
    public ResponseEntity<List<EstagioResponse>> listarTodos(Authentication authentication) {
        return ResponseEntity.ok(estagioService.listarEstagiosDoAluno(authentication.getName()));
    }
}
