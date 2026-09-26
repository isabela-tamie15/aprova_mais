package tcc.ges.aprovamais.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;


@Getter
@AllArgsConstructor
public class ConfiguracaoDoisFatoresResponse {

    private final String otpauthUri;
    private final String segredo;
}
