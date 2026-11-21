package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

// Painel de correção do orientador
@Getter
@Builder
public class PainelCorrecaoResponse {
    // Total de entregas ENVIADA ou EM_REVISAO dos alunos do orientador, sem filtro
    private final long totalAguardando;
    // Total depois dos filtros, para a paginação
    private final long totalFiltrado;
    private final int pagina;
    private final int tamanho;
    private final int totalPaginas;
    private final List<ItemPainelCorrecaoResponse> itens;
    // Opções dos filtros, aqui só alunos e tarefas que aparecem nas entregas do orientador
    private final List<OpcaoFiltroResponse> alunos;
    private final List<OpcaoFiltroResponse> tarefas;
}
