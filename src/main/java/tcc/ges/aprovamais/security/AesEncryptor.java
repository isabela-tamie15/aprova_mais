package tcc.ges.aprovamais.security;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

// Cifra os atributos marcados antes de salvar no banco e decifra ao ler
@Converter(autoApply = false)
@Component
public class AesEncryptor implements AttributeConverter<String, String> {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANHO_TAG_GCM = 128;
    private static final int TAMANHO_IV = 12;

    @Value("${encryption.secret}")
    private String segredo;

    private byte[] bytesChaveSecreta;

    // Prepara a chave quando a aplicação sobe. O segredo passa por um SHA-256 para virar a chave de 32 bytes do AES
    @PostConstruct
    public void inicializar() {
        if (segredo == null || segredo.isBlank()) {
            throw new IllegalStateException("encryption.secret não pode ser nulo ou vazio.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            bytesChaveSecreta = digest.digest(
                    segredo.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
        } catch (Exception excecao) {
            throw new IllegalStateException("Erro ao derivar chave AES.", excecao);
        }
    }

    // Chamado pelo JPA antes de salvar, para cifrar o valor
    @Override
    public String convertToDatabaseColumn(String atributo) {
        if (atributo == null) return null;
        try {
            // IV aleatório a cada cifragem, para o mesmo texto nunca gerar o mesmo valor cifrado
            byte[] iv = new byte[TAMANHO_IV];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(bytesChaveSecreta, "AES"),
                    new GCMParameterSpec(TAMANHO_TAG_GCM, iv));

            byte[] cifrado = cipher.doFinal(
                    atributo.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Junta o IV com o texto cifrado. O IV não é segredo e fica salvo junto, separado de novo ao decifrar
            ByteBuffer buffer = ByteBuffer.allocate(TAMANHO_IV + cifrado.length);
            buffer.put(iv);
            buffer.put(cifrado);

            // Codifica em Base64 para virar texto e poder ser salvo numa coluna VARCHAR
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception excecao) {
            throw new RuntimeException("Falha na cifragem do atributo.", excecao);
        }
    }

    // Chamado pelo JPA ao ler do banco, para decifrar o valor
    @Override
    public String convertToEntityAttribute(String dadosBanco) {
        if (dadosBanco == null) return null;
        try {
            ByteBuffer buffer = ByteBuffer.wrap(
                    Base64.getDecoder().decode(dadosBanco));

            // Os primeiros bytes são o IV, o resto é o texto cifrado
            byte[] iv = new byte[TAMANHO_IV];
            buffer.get(iv);

            byte[] cifrado = new byte[buffer.remaining()];
            buffer.get(cifrado);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(bytesChaveSecreta, "AES"),
                    new GCMParameterSpec(TAMANHO_TAG_GCM, iv));

            return new String(cipher.doFinal(cifrado),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception excecao) {
            throw new RuntimeException("Falha na decifragem do atributo.", excecao);
        }
    }
}