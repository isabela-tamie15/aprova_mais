package tcc.ges.aprovamais.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.OffsetDateTime;
import java.util.Optional;

@Configuration
@EnableAsync
@EnableJpaAuditing(dateTimeProviderRef = "offsetDateTimeProvider")
public class AsyncConfig {

    @Bean
    public DateTimeProvider offsetDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}