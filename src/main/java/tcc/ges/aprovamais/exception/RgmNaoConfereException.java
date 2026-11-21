package tcc.ges.aprovamais.exception;

/*
   RGM informado no primeiro acesso diferente do pré-cadastro. Vira 400, e a transação do primeiro
   acesso é confirmada mesmo com a exceção, para a tentativa errada ficar gravada no convite
*/
public class RgmNaoConfereException extends DadoInvalidoException {

    public RgmNaoConfereException(String mensagem) {
        super(mensagem);
    }
}
