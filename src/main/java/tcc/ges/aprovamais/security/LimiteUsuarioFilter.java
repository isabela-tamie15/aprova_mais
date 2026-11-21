package tcc.ges.aprovamais.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LimiteUsuarioFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LimiteUsuarioFilter.class);

    private static final long MILISSEGUNDOS_POR_JANELA = 60_000L;

    private final Map<String, Contagem> contagemPorUsuario = new ConcurrentHashMap<>();

    @Value("${app.limite.requisicoes-por-usuario-por-minuto:300}")
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
        return !requisicao.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest requisicao,
                                    @NonNull HttpServletResponse resposta,
                                    @NonNull FilterChain cadeiaFiltros) throws ServletException, IOException {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || autenticacao instanceof AnonymousAuthenticationToken || !autenticacao.isAuthenticated()) {
            cadeiaFiltros.doFilter(requisicao, resposta);
            return;
        }

        long janelaAtual = System.currentTimeMillis() / MILISSEGUNDOS_POR_JANELA;
        Contagem contagem = contagemPorUsuario.compute(autenticacao.getName(), (chave, atual) ->
                atual == null || atual.janela != janelaAtual ? new Contagem(janelaAtual) : atual);

        if (contagem.requisicoes.incrementAndGet() > limitePorMinuto) {
            if (contagem.requisicoes.get() == limitePorMinuto + 1) {
                log.warn("[LIMITE] Um usuário passou de {} requisições por minuto na API", limitePorMinuto);
            }
            long segundosParaLiberar = ((janelaAtual + 1) * MILISSEGUNDOS_POR_JANELA - System.currentTimeMillis()) / 1000 + 1;
            resposta.setStatus(429);
            resposta.setHeader("Retry-After", String.valueOf(segundosParaLiberar));
            resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
            resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
            resposta.getWriter().write(String.format(
                    "{\"status\":429,\"erro\":\"Too Many Requests\",\"mensagem\":\"%s\",\"timestamp\":\"%s\"}",
                    "Muitas requisições em pouco tempo. Aguarde um minuto e tente novamente.",
                    OffsetDateTime.now(ZoneOffset.UTC)));
            return;
        }
        cadeiaFiltros.doFilter(requisicao, resposta);
    }

    @Scheduled(fixedDelay = 300_000L)
    public void limparContagensAntigas() {
        long janelaAtual = System.currentTimeMillis() / MILISSEGUNDOS_POR_JANELA;
        contagemPorUsuario.values().removeIf(contagem -> contagem.janela < janelaAtual - 1);
    }
}
