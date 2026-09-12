package tcc.ges.aprovamais.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TipoEstagioResponse {

    private Long id;
    private String nome;
    private BigDecimal cargaHorariaSemanal;
}