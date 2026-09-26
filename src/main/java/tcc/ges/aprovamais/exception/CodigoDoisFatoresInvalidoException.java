package tcc.ges.aprovamais.exception;


public class CodigoDoisFatoresInvalidoException extends RuntimeException {

    public CodigoDoisFatoresInvalidoException() {
        super("Código de verificação inválido.");
    }
}
