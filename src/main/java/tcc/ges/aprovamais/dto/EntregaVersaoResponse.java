package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

// Uma versão da entrega no histórico, com o envio do aluno e se já houve, a avaliação do orientador
@Getter
@Builder
public class EntregaVersaoResponse {
    private final Long id;
    private final Integer numero;
    private final String nomeArquivo;
    private final String tipoArquivo;
    private final Long tamanhoBytes;
    private final String comentarioAluno;
    private final OffsetDateTime enviadaEm;

    // Avaliação: resultado APROVADA ou DEVOLVIDA; tudo nulo enquanto a versão não foi avaliada
    private final String resultado;
    private final String feedback;
    private final String nomeAvaliador;
    private final OffsetDateTime avaliadaEm;
}
