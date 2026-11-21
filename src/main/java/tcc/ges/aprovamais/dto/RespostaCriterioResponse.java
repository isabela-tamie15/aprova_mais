package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

// Resultado de um critério em uma versão corrigida, mostrado ao aluno e ao orientador no histórico
@Getter
@Builder
public class RespostaCriterioResponse {
    // Descrição copiada no dia da correção, e não a descrição atual do critério
    private final String descricaoCriterio;

    // SUFICIENTE ou INSUFICIENTE
    private final String resultado;
    private final String observacao;
}
