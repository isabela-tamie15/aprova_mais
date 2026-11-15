package tcc.ges.aprovamais.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tcc.ges.aprovamais.dto.ConsultaCnpjResponse;
import tcc.ges.aprovamais.dto.EstagioCadastroRequest;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.dto.TipoEstagioResponse;
import tcc.ges.aprovamais.dto.ValidacaoEstagioResponse;
import tcc.ges.aprovamais.exception.DadoInvalidoException;
import tcc.ges.aprovamais.external.ConsultaCnpjService;
import tcc.ges.aprovamais.service.EstagioService;
import tcc.ges.aprovamais.service.ValidadorCnpj;

import java.util.List;
import java.util.Optional;

// Estágio do aluno
@Tag(name = "Estágio do aluno", description = "Cadastro, consulta e reenvio do estágio pelo aluno")
@RestController
@RequestMapping("/api/v1/aluno/estagio")
@RequiredArgsConstructor
public class AlunoEstagioController {

    private final EstagioService estagioService;
    private final ConsultaCnpjService consultaCnpjService;

    // Devolve os tipos de estágio que o aluno pode escolher, só os do curso dele
    @GetMapping("/tipos")
    public ResponseEntity<List<TipoEstagioResponse>> listarTiposEstagio(Authentication authentication) {
        List<TipoEstagioResponse> tipos = estagioService.listarTiposEstagioDisponiveis(authentication.getName());
        return ResponseEntity.ok(tipos);
    }

    // Devolve o estágio atual do aluno logado
    @GetMapping
    public ResponseEntity<EstagioResponse> buscarMeuEstagio(Authentication authentication) {

        // O e-mail vem do token, então o aluno não consegue ver o estágio de outro
        String email = authentication.getName();
        Optional<EstagioResponse> estagioEncontrado = estagioService.buscarEstagioDoAluno(email);

        // Sem estágio, a resposta é 204 No Content
        if (estagioEncontrado.isPresent()) {
            EstagioResponse resposta = estagioEncontrado.get();
            return ResponseEntity.ok(resposta);
        } else {
            return ResponseEntity.noContent().build();
        }
    }

    // Cadastra um estágio novo ou reenvia o que foi rejeitado
    @PostMapping
    public ResponseEntity<EstagioResponse> cadastrarOuAtualizar(
            Authentication authentication,
            @Valid @RequestBody EstagioCadastroRequest request,
            HttpServletRequest httpRequest) {

        /*
           Mesma ideia do buscar, e-mail vem do token. O IP também vai para o
           service para ficar registrado na auditoria
        */
        String email = authentication.getName();

        // A consulta à BrasilAPI é feita antes, fora da transação do cadastro
        ConsultaCnpjResponse consultaNaReceita = consultaCnpjService.consultar(
                ValidadorCnpj.normalizar(request.getCnpjEmpresa()));

        EstagioResponse resposta = estagioService.cadastrarOuAtualizarEstagio(
                email, request, consultaNaReceita, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(resposta);
    }

    // Esse endpoint que devolve as aprovações e rejeições do estágio do aluno logado, da mais antiga para mais recente
    @GetMapping("/historico")
    public ResponseEntity<List<ValidacaoEstagioResponse>> historico(Authentication authentication) {
        return ResponseEntity.ok(estagioService.listarHistoricoDoAluno(authentication.getName()));
    }

    // Lista todos os estágios da matrícula do aluno, o atual e os já concluídos ou encerrados
    @GetMapping("/todos")
    public ResponseEntity<List<EstagioResponse>> listarTodos(Authentication authentication) {
        return ResponseEntity.ok(estagioService.listarEstagiosDoAluno(authentication.getName()));
    }


    // Chamado quando o aluno termina de digitar o CNPJ, para preencher o nome da empresa com a razão social
    @GetMapping("/cnpj/{cnpj}")
    public ResponseEntity<ConsultaCnpjResponse> consultarCnpj(@PathVariable String cnpj) {
        if (!ValidadorCnpj.valido(cnpj)) {
            throw new DadoInvalidoException("CNPJ inválido. Confira os números digitados");
        }
        return ResponseEntity.ok(consultaCnpjService.consultar(cnpj));
    }
}
