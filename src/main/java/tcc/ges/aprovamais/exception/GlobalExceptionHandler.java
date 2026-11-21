package tcc.ges.aprovamais.exception;

import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tcc.ges.aprovamais.service.BloqueioContaService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

// Transforma as exceções em respostas JSON padronizadas
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

    public static boolean ehPagina(HttpServletRequest requisicao) {
        String aceita = requisicao.getHeader("Accept");
        return "GET".equals(requisicao.getMethod())
                && aceita != null && aceita.contains("text/html");
    }

    private static ModelAndView paginaDeErro(HttpStatus status, String mensagem) {
        ModelAndView pagina = new ModelAndView("error", status);
        pagina.addObject("status", status.value());
        pagina.addObject("mensagem", mensagem);
        return pagina;
    }

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
                        "E-mail ou senha inválidos. Depois de " + BloqueioContaService.maximoTentativas()
                                + " tentativas erradas, o acesso fica bloqueado por " + BloqueioContaService.minutosBloqueio()
                                + " minutos."));
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
           Cada campo com erro entra no mapa "campos", com o nome do campo e a mensagem, para a tela
           mostrar o erro embaixo do campo certo. A "mensagem" traz o primeiro erro para as telas que
           só leem dados.mensagem. O LinkedHashMap mantém a ordem em que os erros foram encontrados
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
       As validações cruzadas ficam em métodos isAlgumaCoisa, e o erro chega com o nome do método.
       Aqui ele é trocado pelo campo que o usuário preencheu, para a tela marcar o campo certo
    */
    private String nomeDoCampo(String campo) {
        return switch (campo) {
            case "dataInicioDentroDoLimite" -> "dataInicio";
            case "cnpjComDigitosValidos" -> "cnpjEmpresa";
            case "senhasConferem" -> "confirmacaoSenha";
            case "senhaDentroDoLimiteDoBcrypt" -> "senha";
            case "turmaInformadaParaAluno" -> "turmaId";
            case "perfilDePreCadastro" -> "perfil";
            case "rgmNumericoParaAluno" -> "identificadorInstitucional";
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

    // 400 parâmetro inválido que não passa pelo @Valid, como o CNPJ na URL
    @ExceptionHandler(DadoInvalidoException.class)
    public ResponseEntity<ErroResposta> handleDadoInvalido(DadoInvalidoException excecao) {
        log.warn("[EXCEPTION] Parâmetro inválido: {}", excecao.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(construirErro(HttpStatus.BAD_REQUEST, excecao.getMessage()));
    }

    // 409 regra de negócio violada (exemplo, estágio já pendente, já analisado etc)
    @ExceptionHandler(RegraNegocioException.class)
    public Object handleRegraNegocio(RegraNegocioException excecao, HttpServletRequest requisicao) {
        log.warn("[EXCEPTION] Regra de negócio violada: {}", excecao.getMessage());
        if (ehPagina(requisicao)) {
            return paginaDeErro(HttpStatus.CONFLICT, excecao.getMessage());
        }
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(construirErro(HttpStatus.CONFLICT, excecao.getMessage()));
    }

    /*
       409 restrição única do banco, como dois envios da mesma entrega ao mesmo tempo. A mensagem do
       banco fica só no log, porque traz nomes de tabela e de coluna
    */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResposta> handleIntegridade(DataIntegrityViolationException excecao) {
        log.warn("[EXCEPTION] Restrição do banco violada: {}", excecao.getMostSpecificCause().getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(construirErro(HttpStatus.CONFLICT,
                        "Esta operação conflita com outra feita ao mesmo tempo. Atualize a página e confira."));
    }

    // 403 acesso negado pelo @PreAuthorize, como um aluno abrindo uma página da coordenação
    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException excecao, HttpServletRequest requisicao) {
        log.warn("[EXCEPTION] Acesso negado: {}", excecao.getMessage());
        if (ehPagina(requisicao)) {
            return paginaDeErro(HttpStatus.FORBIDDEN, null);
        }
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(construirErro(HttpStatus.FORBIDDEN, "Você não tem permissão para acessar este recurso."));
    }

    // 404 recurso não encontrado
    @ExceptionHandler(ResourceNotFoundException.class)
    public Object handleNotFound(ResourceNotFoundException excecao, HttpServletRequest requisicao) {
        log.warn("[EXCEPTION] Recurso não encontrado: {}", excecao.getMessage());
        if (ehPagina(requisicao)) {
            return paginaDeErro(HttpStatus.NOT_FOUND, excecao.getMessage());
        }
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(construirErro(HttpStatus.NOT_FOUND, excecao.getMessage()));
    }

    // 404 endereço que não existe
    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleRotaInexistente(NoResourceFoundException excecao, HttpServletRequest requisicao) {
        if (ehPagina(requisicao)) {
            return paginaDeErro(HttpStatus.NOT_FOUND, null);
        }
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(construirErro(HttpStatus.NOT_FOUND, "Endereço não encontrado."));
    }

    // 400 parâmetro da URL ausente ou em formato errado, como um texto no lugar do código
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public Object handleParametroInvalido(Exception excecao, HttpServletRequest requisicao) {
        log.warn("[EXCEPTION] Parâmetro inválido: {}", excecao.getMessage());
        if (ehPagina(requisicao)) {
            return paginaDeErro(HttpStatus.NOT_FOUND, null);
        }
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(construirErro(HttpStatus.BAD_REQUEST, "Parâmetro ausente ou inválido na requisição."));
    }

    // 405 método HTTP que o endereço não aceita
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMetodoNaoSuportado(HttpRequestMethodNotSupportedException excecao, HttpServletRequest requisicao) {
        if (ehPagina(requisicao)) {
            return paginaDeErro(HttpStatus.NOT_FOUND, null);
        }
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(construirErro(HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido para este endereço."));
    }

    // 500 erro genérico nao tratado
    @ExceptionHandler(Exception.class)
    public Object handleGeneric(Exception excecao, HttpServletRequest requisicao) {

        /*
           Último handler da cadeia, para o que não foi tratado em nenhum outro lugar. Registra o stack
           completo no log e devolve uma mensagem genérica, sem expor detalhes internos
        */
        log.error("[EXCEPTION] Erro inesperado: {}", excecao.getMessage(), excecao);
        if (ehPagina(requisicao)) {
            return paginaDeErro(HttpStatus.INTERNAL_SERVER_ERROR, null);
        }
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(construirErro(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Erro interno. Tente novamente mais tarde."
                ));
    }
}