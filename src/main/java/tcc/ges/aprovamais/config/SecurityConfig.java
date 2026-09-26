package tcc.ges.aprovamais.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tcc.ges.aprovamais.security.JwtFilter;

import java.util.List;

// Essa config é quem define as regras de segurança da aplicação inteira, incluido as rotas permitidas
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    // Esse é o bean principal que monta a cadeia de filtros do Spring Security
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationProvider authenticationProvider,
            Environment env) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                /*
                   Desliga as proteções que não usamos. Como a API é stateless
                   e autentica por JWT no cookie, CSRF e form login padrão só
                   atrapalhariam
                */
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                // Sem sessão no servidor, cada requisição se autentica sozinha pelo token
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        /*
                           Rotas públicas, que qualquer um acessa sem estar logado.
                           Aqui entram login, telas de consentimento, arquivos estáticos
                           e os endpoints de 2fa, que rodam antes do login terminar
                        */
                        .requestMatchers(
                                "/login",
                                "/error",
                                "/favicon.ico",
                                "/css/**",
                                "/js/**",
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
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/health"
                        ).permitAll()
                        /*
                           Aqui começa a proteção por perfil, cada rota só aceita
                           quem tem o papel certo. O lgpd é o único que libera
                           pra vários perfis ao mesmo tempo
                        */
                        .requestMatchers("/api/v1/coordenador/**").hasRole("COORDENADOR")
                        .requestMatchers("/api/v1/orientador/**").hasRole("ORIENTADOR")
                        .requestMatchers("/secretaria/**").hasRole("SECRETARIA")
                        .requestMatchers("/api/v1/convites/**").hasRole("SECRETARIA")
                        .requestMatchers("/api/v1/lgpd/**").hasAnyRole("ALUNO", "ORIENTADOR", "COORDENADOR", "SECRETARIA")
                        .requestMatchers("/api/v1/aluno/**").hasRole("ALUNO")
                        // Qualquer outra rota que não bateu acima exige pelo menos estar logado
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider)
                // Coloca o filtro do JWT antes do filtro padrão de login por usuário e senha
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        /*
           Só força HTTPS em produção. Em dev fica livre pra rodar em HTTP,
           senão atrapalharia o desenvolvimento local
        */
        if (env.acceptsProfiles(Profiles.of("prod"))) {
            http.requiresChannel(channel -> channel
                    .anyRequest().requiresSecure()
            );
        }
        return http.build();
    }

    // Esse é o provider que autentica de verdade, usando o UserDetailsService e o BCrypt
    @Bean
    public AuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    // Esse bean é o que o AuthService usa pra disparar a autenticação no login
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // Usa BCrypt pra fazer o hash das senhas, que é o padrão recomendado hoje
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Config de CORS, define quais origens podem chamar a API e como
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        /*
           Só essas duas origens podem chamar a API. O allowCredentials é
           necessário pra que o cookie JWT seja enviado nas requisições
        */
        config.setAllowedOrigins(List.of(
                "http://localhost:8080",
                "https://localhost:8443"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}