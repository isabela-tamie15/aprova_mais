package tcc.ges.aprovamais.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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

        final String cabecalhoAutorizacao = requisicao.getHeader("Authorization");

        if (cabecalhoAutorizacao == null || !cabecalhoAutorizacao.startsWith("Bearer ")) {
            cadeiaFiltros.doFilter(requisicao, resposta);
            return;
        }

        final String token = cabecalhoAutorizacao.substring(7);
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
}