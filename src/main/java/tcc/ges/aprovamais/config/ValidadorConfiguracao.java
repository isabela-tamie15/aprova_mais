package tcc.ges.aprovamais.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/*
   Confere a configuração de segurança assim que a aplicação sobe. Sem o perfil, ou com uma
   chave curta, fraca ou igual a um exemplo da documentação, a aplicação não sobe e o log diz o motivo
*/
@Component
public class ValidadorConfiguracao implements InitializingBean {

    // Perfis do projeto, dev na máquina local, prod no servidor e test nos testes automatizados
    private static final List<String> PERFIS_CONHECIDOS = List.of("dev", "prod", "test");

    // Tamanho mínimo da chave do JWT (HMAC-SHA256), usado também para a chave do AES
    static final int TAMANHO_MINIMO_CHAVE = 32;

    // Uma chave com poucos caracteres diferentes, como 32 letras "a", é fácil de adivinhar
    static final int MINIMO_CARACTERES_DIFERENTES = 10;

    // Trechos dos exemplos do README e do .env.example, que não podem ir para um ambiente de verdade
    private static final List<String> TRECHOS_DE_EXEMPLO = List.of(
            "aprova_mais_chave_secreta", "troque_por", "sua_chave", "changeme", "exemplo");

    private final Environment ambiente;

    public ValidadorConfiguracao(Environment ambiente) {
        this.ambiente = ambiente;
    }

    @Override
    public void afterPropertiesSet() {
        validarPerfil();
        validarChave("JWT_SECRET (jwt.secret)", ambiente.getProperty("jwt.secret"));
        validarChave("ENCRYPTION_SECRET (encryption.secret)", ambiente.getProperty("encryption.secret"));

        // No servidor, o link dos e-mails (convite, recuperação de senha) precisa ser HTTPS
        if (perfilAtivo("prod")) {
            String url = ambiente.getProperty("app.url", "");
            if (!url.startsWith("https://")) {
                throw new IllegalStateException("No perfil prod, APP_URL deve começar com https://");
            }
        }
    }

    private void validarPerfil() {
        for (String perfil : ambiente.getActiveProfiles()) {
            if (PERFIS_CONHECIDOS.contains(perfil)) {
                return;
            }
        }
        throw new IllegalStateException(
                "Nenhum perfil ativo. Defina SPRING_PROFILES_ACTIVE como dev (máquina local) ou prod (servidor)");
    }

    // A mensagem cita só o nome da variável, nunca o valor dela
    static void validarChave(String nome, String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(nome + " não foi configurada");
        }
        if (valor.getBytes(StandardCharsets.UTF_8).length < TAMANHO_MINIMO_CHAVE) {
            throw new IllegalStateException(nome + " deve ter pelo menos " + TAMANHO_MINIMO_CHAVE + " bytes");
        }

        Set<Character> caracteresDiferentes = new HashSet<>();
        for (char caractere : valor.toCharArray()) {
            caracteresDiferentes.add(caractere);
        }
        if (caracteresDiferentes.size() < MINIMO_CARACTERES_DIFERENTES) {
            throw new IllegalStateException(nome + " é fraca demais: gere um valor aleatório, por exemplo com openssl rand -base64 48");
        }

        String emMinusculas = valor.toLowerCase(Locale.ROOT);
        for (String trecho : TRECHOS_DE_EXEMPLO) {
            if (emMinusculas.contains(trecho)) {
                throw new IllegalStateException(nome + " ainda está com o valor de exemplo da documentação");
            }
        }
    }

    private boolean perfilAtivo(String perfil) {
        for (String ativo : ambiente.getActiveProfiles()) {
            if (ativo.equals(perfil)) {
                return true;
            }
        }
        return false;
    }
}
