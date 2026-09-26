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

// Esse filtro roda em toda requisição, é ele quem lê o JWT e autentica o usuário antes do controller
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    // Esse é o método principal do filtro, chamado uma vez por requisição
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest requisicao,
            @NonNull HttpServletResponse resposta,
            @NonNull FilterChain cadeiaFiltros
    ) throws ServletException, IOException {

        String token = extrairToken(requisicao);

        // Sem token, segue o fluxo como anônimo e deixa o Security decidir o que fazer
        if (token == null) {
            cadeiaFiltros.doFilter(requisicao, resposta);
            return;
        }

        final String email;
        try {
            /*
               Extrai o e-mail de dentro do token, o que também já valida a
               assinatura dele. Se o token tiver adulterado ou inválido, cai
               no catch e a requisição segue sem autenticação
            */
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
                // E-mail do token não existe mais (tipo conta anonimizada), segue sem autenticar
                cadeiaFiltros.doFilter(requisicao, resposta);
                return;
            }

            /*
               Conta desativada (anonimizada ou desativada pelo admin) perde
               o acesso na hora, mesmo que o token ainda esteja dentro do prazo.
               Já as contas bloqueadas por tentativa de login continuam com a
               sessão atual, porque o bloqueio protege o login contra força
               bruta, não tem a ver com encerrar sessões que já estão abertas
            */
            if (!usuarioDetalhes.isEnabled()) {
                log.warn("[AUTH] Token recusado para conta inativa: {}", email);
                cadeiaFiltros.doFilter(requisicao, resposta);
                return;
            }

            // Monta a autenticação com o usuário e as permissões dele
            UsernamePasswordAuthenticationToken autenticacao =
                    new UsernamePasswordAuthenticationToken(
                            usuarioDetalhes,
                            null,
                            usuarioDetalhes.getAuthorities()
                    );

            // Anexa os detalhes da requisição, tipo IP e sessão, na autenticação
            autenticacao.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(requisicao)
            );

            SecurityContextHolder.getContext().setAuthentication(autenticacao);
        }

        // Segue pro próximo filtro ou pro controller, agora com ou sem autenticação
        cadeiaFiltros.doFilter(requisicao, resposta);
    }

    // Esse é o auxiliar que procura o token primeiro no header, depois nos cookies
    private String extrairToken(HttpServletRequest requisicao) {

        /*
           O header Authorization com Bearer é o padrão pra APIs. Mas como a
           gente usa cookie HttpOnly nas páginas, também aceita o token vindo
           por cookie, senão o navegador não conseguiria autenticar
        */
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