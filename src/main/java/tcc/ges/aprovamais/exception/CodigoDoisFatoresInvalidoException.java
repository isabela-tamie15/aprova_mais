package tcc.ges.aprovamais.exception;


// Código do 2FA errado, sempre com a mesma mensagem
public class CodigoDoisFatoresInvalidoException extends RuntimeException {

    // Construtor sem parâmetros, a mensagem já é padrão
    public CodigoDoisFatoresInvalidoException() {
        super("Código de verificação inválido.");
    }
}
