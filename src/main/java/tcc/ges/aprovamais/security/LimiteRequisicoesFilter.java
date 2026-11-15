package tcc.ges.aprovamais.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/*
   Limite de requisições por IP nas rotas de login, 2FA, recuperação de senha, primeiro acesso
   e consulta de CNPJ. O valor é alto porque os alunos do laboratório saem pelo mesmo IP
*/
// Roda antes dos filtros do Spring Security
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class LimiteRequisicoesFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LimiteRequisicoesFilter.class);

    private static final long MILISSEGUNDOS_POR_JANELA = 60_000L;

    private static final List<String> ROTAS_LIMITADAS = List.of(
            "/api/v1/auth/",
            "/primeiro-acesso/aceitar",
            "/api/v1/aluno/estagio/cnpj/");

    // Contagem de cada IP no minuto atual
    private final Map<String, Contagem> contagemPorIp = new ConcurrentHashMap<>();

    @Value("${app.limite.requisicoes-por-minuto:250}")
    private int limitePorMinuto;

    private static final class Contagem {
        private final long janela;
        private final AtomicInteger requisicoes = new AtomicInteger();

        private Contagem(long janela) {
            this.janela = janela;
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest requisicao) {
        String caminho = requisicao.getRequestURI();
        for (String rota : ROTAS_LIMITADAS) {
            if (caminho.startsWith(rota)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest requisicao,
                                    @NonNull HttpServletResponse resposta,
                                    @NonNull FilterChain cadeiaFiltros) throws ServletException, IOException {
        long janelaAtual = System.currentTimeMillis() / MILISSEGUNDOS_POR_JANELA;
        String ip = requisicao.getRemoteAddr();

        // O compute troca a contagem de forma atômica quando o minuto vira
        Contagem contagem = contagemPorIp.compute(ip, (chave, atual) ->
                atual == null || atual.janela != janelaAtual ? new Contagem(janelaAtual) : atual);

        if (contagem.requisicoes.incrementAndGet() > limitePorMinuto) {
            long segundosParaLiberar = ((janelaAtual + 1) * MILISSEGUNDOS_POR_JANELA - System.currentTimeMillis()) / 1000 + 1;
            if (contagem.requisicoes.get() == limitePorMinuto + 1) {
                log.warn("[LIMITE] IP {} passou de {} requisições por minuto em rotas de autenticação", ip, limitePorMinuto);
            }
            responderLimiteExcedido(resposta, segundosParaLiberar);
            return;
        }
        cadeiaFiltros.doFilter(requisicao, resposta);
    }

    private void responderLimiteExcedido(HttpServletResponse resposta, long segundosParaLiberar) throws IOException {
        resposta.setStatus(429);
        resposta.setHeader("Retry-After", String.valueOf(segundosParaLiberar));
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resposta.getWriter().write(String.format(
                "{\"status\":429,\"erro\":\"Too Many Requests\",\"mensagem\":\"%s\",\"timestamp\":\"%s\"}",
                "Muitas tentativas em pouco tempo. Aguarde um minuto e tente novamente.",
                OffsetDateTime.now(ZoneOffset.UTC)));
    }

    // A cada 5 minutos, tira da memória os IPs antigos
    @Scheduled(fixedDelay = 300_000L)
    public void limparContagensAntigas() {
        long janelaAtual = System.currentTimeMillis() / MILISSEGUNDOS_POR_JANELA;
        contagemPorIp.values().removeIf(contagem -> contagem.janela < janelaAtual - 1);
    }
}
