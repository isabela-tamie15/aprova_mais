package tcc.ges.aprovamais.security;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
   Lista dos tokens encerrados pelo logout. Sem ela, um token copiado antes do
   logout continuaria valendo até expirar (8 horas), porque o JWT não tem
   estado no servidor. Cada token fica na lista só até a própria expiração.

   A lista fica na memória da aplicação, o que basta para uma instância só.
   Com mais de uma instância, ela passa para o Redis, que já está no
   docker-compose
*/
@Component
@RequiredArgsConstructor
public class TokenRevogadoService {

    private final JwtService jwtService;

    // Hash do token e o momento em que ele expira
    private final Map<String, Instant> revogados = new ConcurrentHashMap<>();

    public void revogar(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            revogados.put(HashToken.sha256(token), jwtService.extrairExpiracao(token));
        } catch (JwtException | IllegalArgumentException e) {
            // Token inválido ou já vencido não precisa entrar na lista
        }
        limparVencidos();
    }

    public boolean estaRevogado(String token) {
        Instant expiracao = revogados.get(HashToken.sha256(token));
        return expiracao != null && expiracao.isAfter(Instant.now());
    }

    private void limparVencidos() {
        Instant agora = Instant.now();
        revogados.values().removeIf(expiracao -> !expiracao.isAfter(agora));
    }
}
