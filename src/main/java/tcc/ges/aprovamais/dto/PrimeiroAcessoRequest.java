package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PrimeiroAcessoRequest {

    @NotBlank(message = "A senha é obrigatória")

    //Mínimo de 8 caracteres com pelo menos uma letra e um número. O máximo de 72 é limite do BCrypt, que ignora o que passar disso (e versões novas do Spring Security recusam a senha), então é melhor avisar já no formulário

    @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
    @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).*$",
            message = "A senha deve ter pelo menos uma letra e um número")
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