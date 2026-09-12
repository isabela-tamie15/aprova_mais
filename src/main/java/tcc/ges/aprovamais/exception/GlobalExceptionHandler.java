package tcc.ges.aprovamais.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

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

    // 400 falha na validação do @Valid
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> erros = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            erros.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.warn("[EXCEPTION] Erro de validação: {}", erros);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(erros);
    }

    // 409 regra de negócio violada (ex.: estágio já pendente, já analisado)
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
        log.error("[EXCEPTION] Erro inesperado: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(construirErro(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Erro interno. Tente novamente mais tarde."
                ));
    }
}