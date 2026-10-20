package tcc.ges.aprovamais.exception;

// Exceção pra dado inválido que não passa pelo @Valid, por exemplo um CNPJ que vem na URL. Vira 400
public class DadoInvalidoException extends RuntimeException {

    public DadoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
