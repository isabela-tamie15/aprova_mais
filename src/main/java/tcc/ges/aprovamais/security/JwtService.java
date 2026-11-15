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
import java.util.UUID;

// Gera e valida os tokens JWT da autenticação
@Service
public class JwtService {

    private static final String CLAIM_PERFIL = "perfil";
    private static final String CLAIM_VERSAO_SESSAO = "versaoSessao";

    @Value("${jwt.secret}")
    private String segredo;

    // Tempo de validade do token em milissegundos, configurado no application.yml
    @Value("${jwt.expiration-ms}")
    private long expiracaoMs;

    private SecretKey chaveAssinatura;

    // Monta a chave de assinatura quando a aplicação sobe. Um segredo curto demais é recusado pela biblioteca e a aplicação não sobe
    @PostConstruct
    public void inicializar() {
        if (segredo == null || segredo.isBlank()) {
            throw new IllegalStateException("jwt.secret não pode ser nulo ou vazio.");
        }
        chaveAssinatura = Keys.hmacShaKeyFor(
                segredo.getBytes(StandardCharsets.UTF_8)
        );
    }

    // Gera o token do login com o e-mail, o perfil e a versão da sessão
    public String gerarToken(String email, String perfil, int versaoSessao) {
        return Jwts.builder()
                .subject(email)
                // O perfil vai no token para o filtro montar a autenticação sem buscar o usuário de novo
                .claim(CLAIM_PERFIL, perfil)
                // Versão da sessão no momento do login, conferida pelo JwtFilter
                .claim(CLAIM_VERSAO_SESSAO, versaoSessao)
                // Id único de cada token, para o logout de um login não encerrar outro feito no mesmo segundo
                .id(UUID.randomUUID().toString())
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

    // Versão da sessão gravada no token. O token sem esse campo vale como versão zero
    public int extrairVersaoSessao(String token) {
        // O JJWT pode devolver o número como Integer, Long ou Double, por isso lemos como Number
        Object versao = extrairClaims(token).get(CLAIM_VERSAO_SESSAO);
        if (versao instanceof Number numero) {
            return numero.intValue();
        }
        return 0;
    }

    // Momento em que o token expira, usado para saber até quando ele precisa ficar na lista de revogados
    public java.time.Instant extrairExpiracao(String token) {
        return extrairClaims(token).getExpiration().toInstant();
    }

    // Confere se o token é válido e se pertence ao e-mail esperado. Assinatura errada ou token expirado devolvem false
    public boolean tokenValido(String token, String email) {
        try {
            return extrairEmail(token).equals(email);
        } catch (JwtException | IllegalArgumentException excecao) {
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
