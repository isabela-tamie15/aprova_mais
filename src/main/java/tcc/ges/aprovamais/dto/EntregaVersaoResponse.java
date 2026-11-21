package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

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

    // Resultado da avaliação, APROVADA ou DEVOLVIDA. Fica tudo nulo enquanto a versão não foi avaliada
    private final String resultado;
    private final String feedback;
    private final String nomeAvaliador;
    private final OffsetDateTime avaliadaEm;

    // Resultado de cada critério nesta versão. Vazio quando a tarefa não tinha critérios na correção
    private final List<RespostaCriterioResponse> checklist;

    // DISPONIVEL, EXCLUSAO_EM_ANALISE ou EXCLUIDO. Fora do DISPONIVEL, ninguém abre o arquivo
    private final String situacaoArquivo;
    private final OffsetDateTime arquivoExcluidoEm;
    private final String nomeQuemAprovouExclusao;

    // Ações do aluno nesta versão
    private final boolean podeCancelarEnvio;
    private final boolean podeSolicitarExclusao;
}
