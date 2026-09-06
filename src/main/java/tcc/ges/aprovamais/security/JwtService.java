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

@Service
public class JwtService {

    private static final String CLAIM_PERFIL = "perfil";

    @Value("${jwt.secret}")
    private String segredo;

    @Value("${jwt.expiration-ms}")
    private long expiracaoMs;

    private SecretKey chaveAssinatura;

    @PostConstruct
    public void inicializar() {
        if (segredo == null || segredo.isBlank()) {
            throw new IllegalStateException("jwt.secret não pode ser nulo ou vazio.");
        }
        chaveAssinatura = Keys.hmacShaKeyFor(
                segredo.getBytes(StandardCharsets.UTF_8)
        );
    }

    public String gerarToken(String email, String perfil) {
        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_PERFIL, perfil)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiracaoMs))
                .signWith(chaveAssinatura)
                .compact();
    }

    public String extrairEmail(String token) {
        return extrairClaims(token).getSubject();
    }

    public String extrairPerfil(String token) {
        return extrairClaims(token).get(CLAIM_PERFIL, String.class);
    }

    public boolean tokenValido(String token, String email) {
        try {
            return extrairEmail(token).equals(email);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims extrairClaims(String token) {
        return Jwts.parser()
                .verifyWith(chaveAssinatura)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}