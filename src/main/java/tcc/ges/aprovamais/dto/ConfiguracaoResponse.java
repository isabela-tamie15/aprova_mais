package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class ConfiguracaoResponse {
    private final String parametro;
    private final String descricao;
    private final String unidade;
    private final int valor;
    private final int padrao;
    private final int minimo;
    private final int maximo;
    private final String nomeAlteradoPor;
    private final OffsetDateTime alteradoEm;
}
