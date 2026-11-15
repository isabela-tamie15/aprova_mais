package tcc.ges.aprovamais.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.OffsetDateTime;
import java.util.Optional;

// Essa config liga três coisas: a execução assíncrona, as rotinas agendadas (@Scheduled) e a auditoria automática do JPA
@Configuration
@EnableAsync
@EnableScheduling
@EnableJpaAuditing(dateTimeProviderRef = "offsetDateTimeProvider")
public class AsyncConfig {

    /*
       O JPA Auditing por padrão usa LocalDateTime para preencher os campos de
       criação e atualização, mas nós usamos OffsetDateTime no projeto. Esse
       bean substitui o provider padrão por um que devolve o formato certo,
       sem isso o Spring nem sobe a aplicação
    */
    @Bean
    public DateTimeProvider offsetDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}