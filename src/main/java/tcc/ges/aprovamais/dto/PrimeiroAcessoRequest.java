package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tcc.ges.aprovamais.security.LimiteSenha;

@Data
public class PrimeiroAcessoRequest {

    @NotBlank(message = "A senha é obrigatória")

    // Mínimo de 8 caracteres, com pelo menos uma letra e um número. O máximo de 72 é o limite do BCrypt

    @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
    @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).*$",
            message = "A senha deve ter pelo menos uma letra e um número")
    private String senha;

    @NotBlank(message = "A confirmação de senha é obrigatória")
    private String confirmacaoSenha;

    // A conferência da tela pode ser contornada chamando o endpoint direto, por isso ela se repete aqui
    @AssertTrue(message = "A senha e a confirmação de senha não conferem")
    public boolean isSenhasConferem() {
        return senha == null || senha.equals(confirmacaoSenha);
    }

    // O limite do BCrypt é em bytes, e não em caracteres (veja LimiteSenha)
    @AssertTrue(message = LimiteSenha.MENSAGEM)
    public boolean isSenhaDentroDoLimiteDoBcrypt() {
        return LimiteSenha.cabeNoBcrypt(senha);
    }
}
