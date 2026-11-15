package tcc.ges.aprovamais.exception;

/*
   Regra de negócio violada, como um estágio que já está pendente ou uma entrega que já foi
   avaliada. Vira 409 com a mensagem para o usuário
*/
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
