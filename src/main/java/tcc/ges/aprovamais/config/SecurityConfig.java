package tcc.ges.aprovamais.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import tcc.ges.aprovamais.exception.GlobalExceptionHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tcc.ges.aprovamais.security.JwtFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

// Regras de segurança da aplicação e rotas liberadas
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    // Porta em que a aplicação recebe HTTP (no Render, a da variável PORT)
    @Value("${server.port:8080}")
    private int portaHttp;

    // Endereço público do sistema (APP_URL), aceito no CORS
    @Value("${app.url}")
    private String appUrl;

    // Monta a cadeia de filtros do Spring Security
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationProvider authenticationProvider,
            Environment env) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                /*
                   A proteção CSRF do Spring fica desligada porque o cookie do login é SameSite=Strict, e o
                   navegador não envia esse cookie em requisições de outros sites. O login por formulário e
                   o login básico também saem, porque a tela de login é própria
                */
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                // Sem sessão no servidor, cada requisição se autentica sozinha pelo token
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(excecoes -> excecoes
                        .authenticationEntryPoint(this::responderNaoAutenticado)
                )
                // O navegador não envia o endereço da página para outros sites (os links de convite e de recuperação levam o token na URL)
                .headers(cabecalhos -> cabecalhos
                        .referrerPolicy(politica -> politica.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(POLITICA_DE_CONTEUDO))
                )
                .authorizeHttpRequests(auth -> auth
                        /*
                           Rotas públicas, acessadas sem login. Entram o login, as telas de consentimento, os arquivos
                           estáticos e os endpoints de 2FA, que rodam antes do login terminar
                        */
                        .requestMatchers(
                                "/",
                                "/inicio",
                                "/login",
                                "/error",
                                "/favicon.ico",
                                "/css/**",
                                "/js/**",
                                "/vendor/**",
                                "/.well-known/**",
                                "/consentimento",
                                "/termos",
                                "/privacidade",
                                "/primeiro-acesso",
                                "/primeiro-acesso/aceitar",
                                "/primeiro-acesso/recusar",
                                "/primeiro-acesso-erro",
                                "/api/v1/auth/login",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/2fa/**",
                                "/api/v1/auth/convite/**",
                                "/recuperar-senha",
                                "/redefinir-senha",
                                "/api/v1/auth/recuperacao/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/livez",
                                "/readyz",
                                "/actuator/health/**",
                                "/actuator/prometheus"
                        ).permitAll()
                        // Proteção por perfil. Cada rota só aceita o perfil certo, e a da LGPD aceita vários
                        .requestMatchers("/admin/**", "/api/v1/admin/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/api/v1/coordenador/**").hasRole("COORDENADOR")
                        .requestMatchers("/api/v1/orientador/**").hasRole("ORIENTADOR")
                        .requestMatchers("/secretaria/**", "/api/v1/secretaria/**").hasRole("SECRETARIA")
                        .requestMatchers("/api/v1/convites/**").hasAnyRole("ADMINISTRADOR", "COORDENADOR", "SECRETARIA")
                        .requestMatchers("/api/v1/lgpd/**").hasAnyRole("ALUNO", "ORIENTADOR", "COORDENADOR", "SECRETARIA")
                        .requestMatchers("/api/v1/aluno/**").hasRole("ALUNO")
                        // Qualquer outra rota que não bateu acima exige pelo menos estar logado
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider)
                // Filtro do JWT antes do filtro padrão de login por usuário e senha
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        /*
           Força HTTPS só em produção, para o desenvolvimento local rodar em HTTP. O /livez e o /readyz
           ficam de fora porque o health check do Render chama por HTTP na rede interna
        */
        if (env.acceptsProfiles(Profiles.of("prod"))) {
            http.redirectToHttps(https -> https
                    .requestMatchers(requisicao -> !ehHealthCheck(requisicao))
            );
            // Redireciona para a porta 443 do domínio público
            http.portMapper(portas -> portas.http(portaHttp).mapsTo(443));
        }
        return http.build();
    }


    private static boolean ehHealthCheck(HttpServletRequest requisicao) {
        String caminho = requisicao.getRequestURI();
        return caminho.equals("/livez") || caminho.equals("/readyz");
    }

    /*
       Política de segurança de conteúdo. As páginas só carregam scripts, estilos e fontes do próprio
       sistema. O Alpine.js avalia as expressões dos atributos das páginas, por isso o
       unsafe-eval, e os estilos aceitam o atributo style das telas. Nenhum script inline é aceito, e a
       página não pode ser aberta dentro de um frame de outro site
    */
    private static final String POLITICA_DE_CONTEUDO = String.join("; ",
            "default-src 'self'",
            "script-src 'self' 'unsafe-eval'",
            "style-src 'self' 'unsafe-inline'",
            "font-src 'self'",
            "img-src 'self' data:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    private void responderNaoAutenticado(HttpServletRequest requisicao,
                                         HttpServletResponse resposta,
                                         AuthenticationException excecao) throws IOException {
        if (!requisicao.getRequestURI().startsWith("/api/") || GlobalExceptionHandler.ehPagina(requisicao)) {
            resposta.sendRedirect("/login");
            return;
        }
        resposta.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resposta.getWriter().write(String.format(
                "{\"status\":401,\"erro\":\"Unauthorized\",\"mensagem\":\"%s\",\"timestamp\":\"%s\"}",
                "Autenticação necessária. Faça login novamente.",
                OffsetDateTime.now(ZoneOffset.UTC)));
    }

    // Provider que autentica com o UserDetailsService e o BCrypt
    @Bean
    public AuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    // Esse bean é o que o AuthService usa para disparar a autenticação no login
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // Usa BCrypt para fazer o hash das senhas, que é o padrão recomendado hoje
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Config de CORS, define quais origens podem chamar a API e como
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        /*
           Só essas origens podem chamar a API. O allowCredentials é
           necessário para que o cookie JWT seja enviado nas requisições
        */
        config.setAllowedOrigins(List.of(
                "http://localhost:8080",
                "https://localhost:8443",
                appUrl
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}