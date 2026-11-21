package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

// Histórico de correções do orientador, da mais recente para a mais antiga, com paginação
@Getter
@Builder
public class HistoricoCorrecoesResponse {
    private final long totalCorrecoes;
    private final int pagina;
    private final int tamanho;
    private final int totalPaginas;
    private final List<ItemHistoricoCorrecaoResponse> itens;
}
