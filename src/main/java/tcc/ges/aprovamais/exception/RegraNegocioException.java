package tcc.ges.aprovamais.exception;

/*
   Exceção de regra de negócio, por exemplo um estágio que já está pendente ou uma
   entrega que já foi avaliada. Vira 409 com a mensagem para o usuário. Antes nós
   usávamos a IllegalStateException, mas ela também é lançada por bibliotecas e por
   erros internos, e aí uma mensagem interna podia chegar na tela com status 409 (AUD-18)
*/
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
