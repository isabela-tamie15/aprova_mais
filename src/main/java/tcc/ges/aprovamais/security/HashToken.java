package tcc.ges.aprovamais.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/*
   Hash SHA-256 dos tokens de convite, de recuperação de senha, de pré-autenticação do 2FA e de
   logout. O banco guarda só o hash, então quem lê o banco ou um backup não consegue montar um link válido
*/
public final class HashToken {

    private HashToken() {
    }

    public static String sha256(String textoOriginal) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(textoOriginal.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException excecao) {
            throw new IllegalStateException("SHA-256 indisponível.", excecao);
        }
    }
}
