package tcc.ges.aprovamais.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

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

            UserDetails usuarioDetalhes =
                    userDetailsService.loadUserByUsername(email);

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