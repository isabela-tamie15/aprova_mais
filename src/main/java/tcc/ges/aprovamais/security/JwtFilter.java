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
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.repository.UsuarioRepository;
import tcc.ges.aprovamais.service.ConsentimentoService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

// Esse filtro roda em toda requisição, é ele quem lê o JWT e autentica o usuário antes do controller
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    // Rotas da API liberadas antes do aceite dos termos
    private static final List<String> ROTAS_SEM_CONSENTIMENTO = List.of(
            "/api/v1/auth/", "/api/v1/consentimento/", "/api/v1/lgpd/");

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final TokenRevogadoService tokenRevogadoService;

    // Esse é o método principal do filtro, chamado uma vez por requisição
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest requisicao,
            @NonNull HttpServletResponse resposta,
            @NonNull FilterChain cadeiaFiltros
    ) throws ServletException, IOException {

        String token = extrairToken(requisicao);

        // Sem token, segue o fluxo como anônimo e deixa o Security decidir o que fazer
        // Sem token, ou com um token encerrado pelo logout, a requisição segue sem autenticação
        if (token == null || tokenRevogadoService.estaRevogado(token)) {
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
        } catch (JwtException | IllegalArgumentException excecao) {
            cadeiaFiltros.doFilter(requisicao, resposta);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null
                && jwtService.tokenValido(token, email)) {

            // E-mail do token não existe mais (tipo conta anonimizada), segue sem autenticar
            Optional<Usuario> usuarioDoToken = usuarioRepository.findByEmail(email);
            if (usuarioDoToken.isEmpty()) {
                cadeiaFiltros.doFilter(requisicao, resposta);
                return;
            }
            Usuario usuario = usuarioDoToken.get();

            // O token só vale se foi emitido na versão de sessão atual da conta
            if (jwtService.extrairVersaoSessao(token) != usuario.versaoSessaoAtual()) {
                log.warn("[AUTH] Token de uma sessão encerrada recusado para: {}", email);
                cadeiaFiltros.doFilter(requisicao, resposta);
                return;
            }

            // Sem o aceite da versão atual dos termos, a API responde 403
            if (exigeConsentimento(requisicao) && ConsentimentoService.precisaConsentir(usuario)) {
                responderSemConsentimento(resposta);
                return;
            }

            UserDetails usuarioDetalhes = UserDetailsServiceImpl.paraUserDetails(usuario);

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

        // Segue para o próximo filtro ou para o controller, agora com ou sem autenticação
        cadeiaFiltros.doFilter(requisicao, resposta);
    }

    // Só a API exige o aceite aqui; login, aceite e LGPD ficam livres
    private boolean exigeConsentimento(HttpServletRequest requisicao) {
        String caminho = requisicao.getRequestURI();
        if (!caminho.startsWith("/api/")) {
            return false;
        }
        for (String livre : ROTAS_SEM_CONSENTIMENTO) {
            if (caminho.startsWith(livre)) {
                return false;
            }
        }
        return true;
    }

    private void responderSemConsentimento(HttpServletResponse resposta) throws IOException {
        resposta.setStatus(HttpServletResponse.SC_FORBIDDEN);
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resposta.getWriter().write(String.format(
                "{\"status\":403,\"erro\":\"Forbidden\",\"mensagem\":\"%s\",\"timestamp\":\"%s\"}",
                "Aceite os termos de uso e a política de privacidade para continuar.",
                OffsetDateTime.now(ZoneOffset.UTC)));
    }

    // Esse é o auxiliar que procura o token primeiro no header, depois nos cookies
    private String extrairToken(HttpServletRequest requisicao) {

        /*
           O header Authorization com Bearer é o padrão para APIs. Mas como nós
           usamos cookie HttpOnly nas páginas, também aceitamos o token vindo
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
