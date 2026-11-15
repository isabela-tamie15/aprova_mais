package tcc.ges.aprovamais.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

// Esse handler centraliza o tratamento de exceções, é quem transforma os erros em respostas JSON padronizadas
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Estrutura padrão para respostas de erros
    private record ErroResposta(
            int status,
            String erro,
            String mensagem,
            OffsetDateTime timestamp
    ) {}

    // Mesmo formato, mais o mapa campo -> mensagem dos erros de validação
    private record ErroValidacao(
            int status,
            String erro,
            String mensagem,
            OffsetDateTime timestamp,
            Map<String, String> campos
    ) {}

    // Monta a resposta de erro no formato padrão, evitando repetição em cada handler
    private ErroResposta construirErro(HttpStatus status, String mensagem) {
        return new ErroResposta(
                status.value(),
                status.getReasonPhrase(),
                mensagem,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    // 401 credenciais inválidas, com a mesma mensagem para e-mail sem cadastro, senha errada e conta bloqueada
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErroResposta> handleBadCredentials(BadCredentialsException excecao) {
        log.warn("[EXCEPTION] Credenciais inválidas: {}", excecao.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(construirErro(HttpStatus.UNAUTHORIZED,
                        "E-mail ou senha inválidos. Depois de 5 tentativas erradas, o acesso fica bloqueado por 5 minutos."));
    }

    // 423 conta bloqueada ou desativada
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErroResposta> handleLocked(LockedException excecao) {
        log.warn("[EXCEPTION] Conta bloqueada: {}", excecao.getMessage());
        return ResponseEntity
                .status(HttpStatus.LOCKED)
                .body(construirErro(HttpStatus.LOCKED, excecao.getMessage()));
    }

    // 400 código do 2FA inválido, então é ativar/desativar pela conta
    @ExceptionHandler(CodigoDoisFatoresInvalidoException.class)
    public ResponseEntity<ErroResposta> handleCodigoDoisFatoresInvalido(
            CodigoDoisFatoresInvalidoException excecao) {
        log.warn("[EXCEPTION] Código 2FA inválido");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(construirErro(HttpStatus.BAD_REQUEST, excecao.getMessage()));
    }

    // 400 falha na validação do @Valid
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroValidacao> handleValidation(
            MethodArgumentNotValidException excecao) {

        /*
           Cada campo com erro vira uma entrada no mapa "campos", o nome do
           campo como chave e a mensagem como valor, para o frontend mostrar o
           erro embaixo do campo certo. A "mensagem" traz o primeiro erro,
           assim as telas que só leem dados.mensagem também mostram algo útil.
           O LinkedHashMap mantém a ordem em que os erros foram encontrados
        */
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError fieldError : excecao.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(nomeDoCampo(fieldError.getField()), fieldError.getDefaultMessage());
        }

        String mensagem = campos.isEmpty()
                ? "Verifique os dados informados."
                : campos.values().iterator().next();

        log.warn("[EXCEPTION] Erro de validação: {}", campos.keySet());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErroValidacao(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                mensagem,
                OffsetDateTime.now(ZoneOffset.UTC),
                campos
        ));
    }

    /*
       As validações cruzadas ficam em métodos isAlgumaCoisa (ex: isDataInicioDentroDoLimite),
       e o erro chega com o nome desse método. Aqui troca pelo campo que o
       usuário de fato preencheu, para a tela marcar o input certo
    */
    private String nomeDoCampo(String campo) {
        return switch (campo) {
            case "dataInicioDentroDoLimite" -> "dataInicio";
            case "cnpjComDigitosValidos" -> "cnpjEmpresa";
            case "senhasConferem" -> "confirmacaoSenha";
            case "senhaDentroDoLimiteDoBcrypt" -> "senha";
            case "turmaInformadaParaAluno" -> "turmaId";
            case "perfilDePreCadastro" -> "perfil";
            case "nomeInformadoParaPerfilAdministrativo" -> "nome";
            default -> campo;
        };
    }

    // 400 corpo da requisição ilegível, por exemplo JSON quebrado ou data fora do formato AAAA-MM-DD
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResposta> handleNotReadable(HttpMessageNotReadableException excecao) {
        log.warn("[EXCEPTION] Corpo da requisição inválido: {}", excecao.getMostSpecificCause().getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(construirErro(HttpStatus.BAD_REQUEST,
                        "Dados enviados em formato inválido. Confira os campos e tente novamente."));
    }

    // 400 arquivo da entrega maior que o limite do spring.servlet.multipart (10 MB)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErroResposta> handleArquivoGrande(MaxUploadSizeExceededException excecao) {
        log.warn("[EXCEPTION] Arquivo acima do limite: {}", excecao.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(construirErro(HttpStatus.BAD_REQUEST, "O arquivo deve ter no máximo 10 MB."));
    }

    // 400 envio de entrega sem o arquivo
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErroResposta> handleArquivoAusente(MissingServletRequestPartException excecao) {
        log.warn("[EXCEPTION] Parte ausente na requisição: {}", excecao.getRequestPartName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(construirErro(HttpStatus.BAD_REQUEST, "Selecione um arquivo para enviar."));
    }

    // 400 parâmetro inválido que não passa pelo @Valid (ex: CNPJ na URL)
    @ExceptionHandler(DadoInvalidoException.class)
    public ResponseEntity<ErroResposta> handleDadoInvalido(DadoInvalidoException excecao) {
        log.warn("[EXCEPTION] Parâmetro inválido: {}", excecao.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(construirErro(HttpStatus.BAD_REQUEST, excecao.getMessage()));
    }

    // 409 regra de negócio violada (exemplo, estágio já pendente, já analisado etc)
    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErroResposta> handleRegraNegocio(RegraNegocioException excecao) {
        log.warn("[EXCEPTION] Regra de negócio violada: {}", excecao.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(construirErro(HttpStatus.CONFLICT, excecao.getMessage()));
    }

    /*
       409 restrição única do banco, por exemplo dois envios da mesma entrega ao mesmo
       tempo (AUD-22). A mensagem do banco fica só no log, porque traz nomes de tabela e
       de coluna, e a tela recebe um texto para atualizar a página e conferir
    */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResposta> handleIntegridade(DataIntegrityViolationException excecao) {
        log.warn("[EXCEPTION] Restrição do banco violada: {}", excecao.getMostSpecificCause().getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(construirErro(HttpStatus.CONFLICT,
                        "Esta operação conflita com outra feita ao mesmo tempo. Atualize a página e confira."));
    }

    /*
       403 acesso negado pelo @PreAuthorize, por exemplo um aluno abrindo uma página
       da coordenação. Sem esse handler, a exceção caía no genérico lá embaixo e
       virava 500. As rotas protegidas só pelo SecurityConfig já respondiam 403
       antes de chegar aqui
    */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResposta> handleAccessDenied(AccessDeniedException excecao) {
        log.warn("[EXCEPTION] Acesso negado: {}", excecao.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(construirErro(HttpStatus.FORBIDDEN, "Você não tem permissão para acessar este recurso."));
    }

    // 404 recurso não encontrado
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErroResposta> handleNotFound(ResourceNotFoundException excecao) {
        log.warn("[EXCEPTION] Recurso não encontrado: {}", excecao.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(construirErro(HttpStatus.NOT_FOUND, excecao.getMessage()));
    }

    // 404 endereço que não existe
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResposta> handleRotaInexistente(NoResourceFoundException excecao) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(construirErro(HttpStatus.NOT_FOUND, "Endereço não encontrado."));
    }

    // 500 erro genérico nao tratado
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResposta> handleGeneric(Exception excecao) {

        /*
           Esse é o último handler da cadeia, se cair aqui é porque alguma
           coisa estourou e não foi tratada em nenhum outro lugar. Registra
           o stack completo com log.error e devolve uma mensagem genérica
           para o cliente, sem expor detalhes internos
        */
        log.error("[EXCEPTION] Erro inesperado: {}", excecao.getMessage(), excecao);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(construirErro(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Erro interno. Tente novamente mais tarde."
                ));
    }
}