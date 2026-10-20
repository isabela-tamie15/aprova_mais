package tcc.ges.aprovamais.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

// Esse handler centraliza o tratamento de exceções, é quem transforma os erros em respostas JSON padronizadas
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Estrutura padrão pra respostas de erros
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

    // 401 credenciais inválidas
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErroResposta> handleBadCredentials(BadCredentialsException ex) {
        log.warn("[EXCEPTION] Credenciais inválidas: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(construirErro(HttpStatus.UNAUTHORIZED, "Credenciais inválidas."));
    }

    // 423 conta bloqueada ou desativada
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErroResposta> handleLocked(LockedException ex) {
        log.warn("[EXCEPTION] Conta bloqueada: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.LOCKED)
                .body(construirErro(HttpStatus.LOCKED, ex.getMessage()));
    }

    // 400 código do 2FA inválido, então é ativar/desativar pela conta
    @ExceptionHandler(CodigoDoisFatoresInvalidoException.class)
    public ResponseEntity<ErroResposta> handleCodigoDoisFatoresInvalido(
            CodigoDoisFatoresInvalidoException ex) {
        log.warn("[EXCEPTION] Código 2FA inválido");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(construirErro(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    // 400 falha na validação do @Valid
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroValidacao> handleValidation(
            MethodArgumentNotValidException ex) {

        /*
           Cada campo com erro vira uma entrada no mapa "campos", o nome do
           campo como chave e a mensagem como valor, pro frontend mostrar o
           erro embaixo do campo certo. A "mensagem" traz o primeiro erro,
           assim as telas que só leem dados.mensagem também mostram algo útil.
           O LinkedHashMap mantém a ordem em que os erros foram encontrados
        */
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
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
       usuário de fato preencheu, pra tela marcar o input certo
    */
    private String nomeDoCampo(String campo) {
        return switch (campo) {
            case "dataInicioDentroDoLimite" -> "dataInicio";
            case "cnpjComDigitosValidos" -> "cnpjEmpresa";
            case "senhasConferem" -> "confirmacaoSenha";
            default -> campo;
        };
    }

    // 400 corpo da requisição ilegível, por exemplo JSON quebrado ou data fora do formato AAAA-MM-DD
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResposta> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("[EXCEPTION] Corpo da requisição inválido: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(construirErro(HttpStatus.BAD_REQUEST,
                        "Dados enviados em formato inválido. Confira os campos e tente novamente."));
    }

    // 400 parâmetro inválido que não passa pelo @Valid (ex: CNPJ na URL)
    @ExceptionHandler(DadoInvalidoException.class)
    public ResponseEntity<ErroResposta> handleDadoInvalido(DadoInvalidoException ex) {
        log.warn("[EXCEPTION] Parâmetro inválido: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(construirErro(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    // 409 regra de negócio violada (exemplo, estágio já pendente, já analisado etc)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErroResposta> handleIllegalState(IllegalStateException ex) {
        log.warn("[EXCEPTION] Regra de negócio violada: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(construirErro(HttpStatus.CONFLICT, ex.getMessage()));
    }

    // 404 recurso não encontrado
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErroResposta> handleNotFound(ResourceNotFoundException ex) {
        log.warn("[EXCEPTION] Recurso não encontrado: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(construirErro(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    // 500 erro genérico nao tratado
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResposta> handleGeneric(Exception ex) {

        /*
           Esse é o último handler da cadeia, se cair aqui é porque alguma
           coisa estourou e não foi tratada em nenhum outro lugar. Registra
           o stack completo com log.error e devolve uma mensagem genérica
           pro cliente, sem expor detalhes internos
        */
        log.error("[EXCEPTION] Erro inesperado: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(construirErro(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Erro interno. Tente novamente mais tarde."
                ));
    }
}