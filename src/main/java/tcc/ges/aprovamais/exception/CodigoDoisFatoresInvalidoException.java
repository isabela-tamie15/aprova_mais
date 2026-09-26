package tcc.ges.aprovamais.exception;


// Exceção lançada quando o código do 2fa tá errado, sempre com a mesma mensagem
public class CodigoDoisFatoresInvalidoException extends RuntimeException {

    // Construtor sem parâmetros, a mensagem já é padrão
    public CodigoDoisFatoresInvalidoException() {
        super("Código de verificação inválido.");
    }
}
