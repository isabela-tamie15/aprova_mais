package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class TipoEstagioResponse {

    private final Long id;
    private final String nome;
    private final String descricao;
    private final BigDecimal cargaHorariaNecessaria;
}