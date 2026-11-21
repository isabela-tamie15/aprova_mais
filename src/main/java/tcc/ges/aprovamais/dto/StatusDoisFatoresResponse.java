package tcc.ges.aprovamais.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StatusDoisFatoresResponse {

    private final boolean ativo;
    private final boolean obrigatorio;
}
