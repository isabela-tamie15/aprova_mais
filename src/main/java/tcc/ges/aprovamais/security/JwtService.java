package tcc.ges.aprovamais.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

// Esse service é quem gera e valida os tokens JWT usados na autenticação
@Service
public class JwtService {

    private static final String CLAIM_PERFIL = "perfil";

    @Value("${jwt.secret}")
    private String segredo;

    // Tempo de validade do token em milissegundos, configurado no application.yml
    @Value("${jwt.expiration-ms}")
    private long expiracaoMs;

    private SecretKey chaveAssinatura;

    /*
       Roda uma vez quando a aplicação sobe pra montar a chave de assinatura.
       O segredo vem do application.yml em texto puro, e aqui ele é convertido
       pro formato de chave HMAC que a lib JWT usa. Se o segredo for muito curto,
       a própria lib rejeita e a aplicação nem sobe, o que é bom
    */
    @PostConstruct
    public void inicializar() {
        if (segredo == null || segredo.isBlank()) {
            throw new IllegalStateException("jwt.secret não pode ser nulo ou vazio.");
        }
        chaveAssinatura = Keys.hmacShaKeyFor(
                segredo.getBytes(StandardCharsets.UTF_8)
        );
    }

    // Esse é o método que gera o token no login, com o e-mail e o perfil dentro dele
    public String gerarToken(String email, String perfil) {
        return Jwts.builder()
                .subject(email)
                /*
                   O perfil vai como claim extra pra depois o filtro conseguir
                   montar a autenticação sem precisar buscar o usuário no banco
                   de novo
                */
                .claim(CLAIM_PERFIL, perfil)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiracaoMs))
                .signWith(chaveAssinatura)
                .compact();
    }

    // Extrai o e-mail do token, que fica no campo subject
    public String extrairEmail(String token) {
        return extrairClaims(token).getSubject();
    }

    // Extrai o perfil que foi gravado como claim no momento da geração
    public String extrairPerfil(String token) {
        return extrairClaims(token).get(CLAIM_PERFIL, String.class);
    }

    /*
       Verifica se o token é válido e se pertence ao e-mail esperado. Aqui a
       gente confia no extrairClaims pra validar assinatura e expiração, se
       qualquer um dos dois estiver errado, ele já lança exceção e a gente
       devolve false em vez de estourar pra cima
    */
    public boolean tokenValido(String token, String email) {
        try {
            return extrairEmail(token).equals(email);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // Auxiliar que faz o trabalho pesado de validar a assinatura e devolver os claims
    private Claims extrairClaims(String token) {
        return Jwts.parser()
                .verifyWith(chaveAssinatura)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}