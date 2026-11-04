package tcc.ges.aprovamais.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Configurações do Spring MVC que não são de segurança
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ConsentimentoInterceptor consentimentoInterceptor;

    /*
       O consentimento vale para as telas de cada perfil. Ficam de fora a
       própria página de consentimento, os termos, a política e a API (as
       telas chamam a API depois de passar por aqui)
    */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(consentimentoInterceptor)
                .addPathPatterns("/aluno/**", "/orientador/**", "/coordenador/**", "/secretaria/**",
                        "/admin/**", "/estagio", "/conta/**");
    }
}
