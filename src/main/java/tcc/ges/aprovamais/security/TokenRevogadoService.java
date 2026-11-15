package tcc.ges.aprovamais.security;

import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
   Lista dos tokens encerrados pelo logout, guardada no Redis pelo prazo de cada token.
   Se o Redis cair, a lista fica na memória por 30 segundos, e nos testes ela fica só na memória
*/
@Component
public class TokenRevogadoService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevogadoService.class);

    private static final String PREFIXO_CHAVE = "aprova:jwt-revogado:";
    private static final long MILISSEGUNDOS_SEM_REDIS = 30_000L;

    private final JwtService jwtService;
    private final StringRedisTemplate redis;
    private final boolean usarRedis;

    // Lista na memória com o hash do token e o momento em que ele expira
    private final Map<String, Instant> revogadosNaMemoria = new ConcurrentHashMap<>();

    // Até quando o Redis deixa de ser tentado depois de uma falha
    private volatile long redisIndisponivelAte = 0L;

    public TokenRevogadoService(JwtService jwtService, StringRedisTemplate redis,
                                @Value("${app.revogacao.redis:true}") boolean usarRedis) {
        this.jwtService = jwtService;
        this.redis = redis;
        this.usarRedis = usarRedis;
    }

    public void revogar(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        Instant expiracao;
        try {
            expiracao = jwtService.extrairExpiracao(token);
        } catch (JwtException | IllegalArgumentException excecao) {
            // Token inválido ou já vencido não precisa entrar na lista
            return;
        }
        Duration tempoAteExpirar = Duration.between(Instant.now(), expiracao);
        if (tempoAteExpirar.isNegative() || tempoAteExpirar.isZero()) {
            return;
        }

        String hash = HashToken.sha256(token);
        // A lista na memória sempre recebe o token, para o logout valer mesmo se o Redis cair
        revogadosNaMemoria.put(hash, expiracao);
        limparVencidosDaMemoria();

        if (redisDisponivel()) {
            try {
                redis.opsForValue().set(PREFIXO_CHAVE + hash, "1", tempoAteExpirar);
            } catch (DataAccessException excecao) {
                marcarRedisIndisponivel(excecao);
            }
        }
    }

    public boolean estaRevogado(String token) {
        String hash = HashToken.sha256(token);
        Instant expiracaoNaMemoria = revogadosNaMemoria.get(hash);
        if (expiracaoNaMemoria != null && expiracaoNaMemoria.isAfter(Instant.now())) {
            return true;
        }

        if (redisDisponivel()) {
            try {
                return Boolean.TRUE.equals(redis.hasKey(PREFIXO_CHAVE + hash));
            } catch (DataAccessException excecao) {
                marcarRedisIndisponivel(excecao);
            }
        }
        return false;
    }

    private boolean redisDisponivel() {
        return usarRedis && System.currentTimeMillis() >= redisIndisponivelAte;
    }

    private void marcarRedisIndisponivel(DataAccessException excecao) {
        redisIndisponivelAte = System.currentTimeMillis() + MILISSEGUNDOS_SEM_REDIS;
        log.error("[AUTH] Redis indisponível, lista de tokens revogados na memória por 30 segundos: {}", excecao.getMessage());
    }

    private void limparVencidosDaMemoria() {
        Instant agora = Instant.now();
        revogadosNaMemoria.values().removeIf(expiracao -> !expiracao.isAfter(agora));
    }
}
