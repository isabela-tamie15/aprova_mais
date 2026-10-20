package tcc.ges.aprovamais.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
   Configuração geral da documentação da API (Swagger UI em /swagger-ui.html).
   A autenticação do sistema usa o JWT no cookie HttpOnly "jwt". Quem já fez
   login pela tela do sistema no mesmo navegador consegue testar os endpoints
   direto pelo Swagger, porque o navegador envia o cookie sozinho. O esquema
   Bearer fica disponível para testes com ferramentas como Postman
   já que o JwtFilter também aceita o token no cabeçalho Authorization.
   No perfil prod o Swagger fica desativado (application-prod.properties)
*/
@Configuration
public class OpenApiConfig {

    public static final String COOKIE_AUTH = "cookieAuth";
    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI aprovaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Aprova+ API")
                        .version("v1")
                        .description("""
                                API do Aprova+, sistema de gestão de estágio supervisionado.
                                Os endpoints estão agrupados pelos mesmos módulos dos requisitos
                                funcionais da monografia. Todas as rotas exigem autenticação,
                                exceto as de login e de primeiro acesso.""")
                        .contact(new Contact().name("Isabela Tamie Shihara e Leonardo Valdir Silva"))
                        .license(new License().name("Uso acadêmico (PFC - UMC)")))
                .components(new Components()
                        .addSecuritySchemes(COOKIE_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("jwt")
                                .description("Cookie HttpOnly gravado no login, válido por 8 horas."))
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Alternativa ao cookie, para testes fora do navegador.")))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
