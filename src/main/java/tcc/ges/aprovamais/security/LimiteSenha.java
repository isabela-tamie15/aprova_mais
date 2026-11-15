package tcc.ges.aprovamais.security;

import java.nio.charset.StandardCharsets;

// O BCrypt aceita até 72 bytes. Letra com acento ocupa 2 bytes, então conferimos em bytes, e não em caracteres
public final class LimiteSenha {

    public static final int MAXIMO_BYTES = 72;

    public static final String MENSAGEM = "A senha deve ter no máximo 72 bytes (letras com acento contam como 2)";

    private LimiteSenha() {
    }

    // Senha nula fica para o @NotBlank do formulário
    public static boolean cabeNoBcrypt(String senha) {
        return senha == null || senha.getBytes(StandardCharsets.UTF_8).length <= MAXIMO_BYTES;
    }
}
