package tcc.ges.aprovamais.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/*
   Hash SHA-256 dos tokens. Nós usamos este mesmo método em quatro lugares:
   convite (ConviteService), recuperação de senha (RecuperacaoSenhaService),
   login com 2fa (DoisFatoresService) e logout (TokenRevogadoService).
   O banco guarda só o hash: quem lê o banco, um backup ou um log de consulta
   não consegue montar o link e entrar no lugar da pessoa
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
