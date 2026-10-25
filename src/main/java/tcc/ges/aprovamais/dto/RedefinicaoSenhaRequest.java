package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RedefinicaoSenhaRequest {

    // Token que veio no link do e-mail. O banco só guarda o hash dele
    @NotBlank(message = "Link de recuperação inválido")
    @Size(max = 200, message = "Link de recuperação inválido")
    private String token;

    // Mesma regra de senha do primeiro acesso: 8 a 72 caracteres, com letra e número
    @NotBlank(message = "A senha é obrigatória")
    @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
    @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).*$",
            message = "A senha deve ter pelo menos uma letra e um número")
    private String senha;

    @NotBlank(message = "A confirmação de senha é obrigatória")
    private String confirmacaoSenha;

    @AssertTrue(message = "A senha e a confirmação de senha não conferem")
    public boolean isSenhasConferem() {
        return senha == null || senha.equals(confirmacaoSenha);
    }
}
