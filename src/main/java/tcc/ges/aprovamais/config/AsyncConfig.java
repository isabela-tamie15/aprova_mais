package tcc.ges.aprovamais.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.OffsetDateTime;
import java.util.Optional;

// Essa config liga duas coisas, a execução assíncrona e auditoria automática do JPA
@Configuration
@EnableAsync
@EnableJpaAuditing(dateTimeProviderRef = "offsetDateTimeProvider")
public class AsyncConfig {

    /*
       O JPA Auditing por padrão usa LocalDateTime pra preencher os campos de
       criação e atualização, mas a gente usa OffsetDateTime no projeto. Esse
       bean substitui o provider padrão por um que devolve o formato certo,
       sem isso o Spring nem sobe a aplicação
    */
    @Bean
    public DateTimeProvider offsetDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}