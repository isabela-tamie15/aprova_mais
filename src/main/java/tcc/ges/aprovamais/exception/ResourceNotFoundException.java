package tcc.ges.aprovamais.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String mensagem) {
        super(mensagem);
    }

    public ResourceNotFoundException(String recurso, Long id) {
        super(recurso + " não encontrado com id: " + id);
    }
}