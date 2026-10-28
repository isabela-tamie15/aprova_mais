package tcc.ges.aprovamais.dto;

import java.util.List;

public record PaginaAuditoriaResponse(
        List<RegistroAuditoriaResponse> itens,
        int pagina,
        int tamanho,
        long totalItens,
        int totalPaginas
) {
}
