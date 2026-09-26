package tcc.ges.aprovamais.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest requisicao,
            @NonNull HttpServletResponse resposta,
            @NonNull FilterChain cadeiaFiltros
    ) throws ServletException, IOException {

        String token = extrairToken(requisicao);

        if (token == null) {
            cadeiaFiltros.doFilter(requisicao, resposta);
            return;
        }

        final String email;
        try {
            email = jwtService.extrairEmail(token);
        } catch (JwtException | IllegalArgumentException e) {
            cadeiaFiltros.doFilter(requisicao, resposta);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null
                && jwtService.tokenValido(token, email)) {

            UserDetails usuarioDetalhes;
            try {
                usuarioDetalhes = userDetailsService.loadUserByUsername(email);
            } catch (UsernameNotFoundException e) {
                // E-mail do token não existe mais (ex.: conta anonimizada): segue sem autenticar
                cadeiaFiltros.doFilter(requisicao, resposta);
                return;
            }

            // Conta desativada (anonimizada ou desativada pelo administrador) perde o acesso
            // imediatamente, mesmo que o token ainda esteja dentro do prazo de validade.
            // Contas bloqueadas por tentativas de login mantêm a sessão atual: o bloqueio
            // protege o login contra força bruta, não encerra sessões legítimas.
            if (!usuarioDetalhes.isEnabled()) {
                log.warn("[AUTH] Token recusado para conta inativa: {}", email);
                cadeiaFiltros.doFilter(requisicao, resposta);
                return;
            }

            UsernamePasswordAuthenticationToken autenticacao =
                    new UsernamePasswordAuthenticationToken(
                            usuarioDetalhes,
                            null,
                            usuarioDetalhes.getAuthorities()
                    );

            autenticacao.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(requisicao)
            );

            SecurityContextHolder.getContext().setAuthentication(autenticacao);
        }

        cadeiaFiltros.doFilter(requisicao, resposta);
    }

    private String extrairToken(HttpServletRequest requisicao) {
        String cabecalho = requisicao.getHeader("Authorization");
        if (cabecalho != null && cabecalho.startsWith("Bearer ")) {
            return cabecalho.substring(7);
        }
        if (requisicao.getCookies() != null) {
            for (Cookie cookie : requisicao.getCookies()) {
                if ("jwt".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}