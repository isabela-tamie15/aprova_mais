package tcc.ges.aprovamais.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.OffsetDateTime;
import java.util.Optional;

// Liga a execução assíncrona, as rotinas agendadas (@Scheduled) e a auditoria automática do JPA
@Configuration
@EnableAsync
@EnableScheduling
@EnableJpaAuditing(dateTimeProviderRef = "offsetDateTimeProvider")
public class AsyncConfig {

    @Bean
    public DateTimeProvider offsetDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}