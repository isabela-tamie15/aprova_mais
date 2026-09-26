package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PrimeiroAcessoRequest {

    @NotBlank(message = "A senha é obrigatória")
    @Size(min = 8, message = "A senha deve ter no mínimo 8 caracteres")
    private String senha;

    @NotBlank(message = "A confirmação de senha é obrigatória")
    private String confirmacaoSenha;

    // Validação no backend: a checagem feita na tela pode ser contornada
    // chamando o endpoint diretamente. Valores nulos ficam a cargo do @NotBlank.
    @AssertTrue(message = "A senha e a confirmação de senha não conferem")
    public boolean isSenhasConferem() {
        return senha == null || senha.equals(confirmacaoSenha);
    }
}