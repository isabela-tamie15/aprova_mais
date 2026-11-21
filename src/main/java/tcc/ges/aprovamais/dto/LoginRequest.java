package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tcc.ges.aprovamais.security.LimiteSenha;

@Data
public class LoginRequest {

    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "E-mail inválido")
    @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres")
    private String email;

    // O BCrypt só considera os primeiros 72 bytes, então nenhuma senha válida passa disso
    @NotBlank(message = "Senha é obrigatória")
    @Size(max = 72, message = "A senha deve ter no máximo 72 caracteres")
    private String senha;

    // O limite do BCrypt é em bytes, e não em caracteres (veja LimiteSenha)
    @AssertTrue(message = LimiteSenha.MENSAGEM)
    public boolean isSenhaDentroDoLimiteDoBcrypt() {
        return LimiteSenha.cabeNoBcrypt(senha);
    }
}
