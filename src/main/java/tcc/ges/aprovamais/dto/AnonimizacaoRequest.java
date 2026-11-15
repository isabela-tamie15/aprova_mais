package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import tcc.ges.aprovamais.security.LimiteSenha;

// Confirmação da anonimização: a senha sempre, e o código quando a verificação em duas etapas está ativa
@Getter
@Setter
public class AnonimizacaoRequest {

    @NotBlank(message = "Digite a sua senha para confirmar")
    @Size(max = 72, message = "A senha deve ter no máximo 72 caracteres")
    private String senha;

    // Vazio para quem não usa a verificação em duas etapas
    @Pattern(regexp = "^$|^\\d{6}$", message = "O código de verificação tem 6 números")
    private String codigo;

    // O limite do BCrypt é em bytes, e não em caracteres (veja LimiteSenha)
    @AssertTrue(message = LimiteSenha.MENSAGEM)
    public boolean isSenhaDentroDoLimiteDoBcrypt() {
        return LimiteSenha.cabeNoBcrypt(senha);
    }
}
