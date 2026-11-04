package tcc.ges.aprovamais.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/*
   Hash SHA-256 dos tokens que vão nos links enviados por e-mail (convite).
   O banco guarda só o hash: quem lê o banco, um backup ou um log de consulta
   não consegue montar o link e entrar no lugar do convidado. É o mesmo
   formato (hexadecimal) do token de recuperação de senha
*/
public final class HashToken {

    private HashToken() {
    }

    public static String sha256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }
}
