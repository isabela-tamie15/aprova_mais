package tcc.ges.aprovamais.exception;

// Recurso não encontrado, como usuário ou estágio
public class ResourceNotFoundException extends RuntimeException {

    // Construtor com mensagem livre, usado quando já se tem o texto pronto
    public ResourceNotFoundException(String mensagem) {
        super(mensagem);
    }

    // Construtor com padrão pronto, monta a mensagem com o nome do recurso e o id
    public ResourceNotFoundException(String recurso, Long id) {
        super(recurso + " não encontrado com id: " + id);
    }
}