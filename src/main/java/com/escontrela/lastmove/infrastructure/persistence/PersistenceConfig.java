package com.escontrela.lastmove.infrastructure.persistence;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures local persistence with graceful degradation.
 *
 * <p>Flyway is disabled in Spring Boot autoconfiguration and invoked manually here. If the SQLite
 * database cannot be created or migrated, the application context still starts and reports the
 * failure through {@link PersistenceAvailability}.
 */
@Configuration
public class PersistenceConfig {

    @Bean
    public PersistenceAvailability persistenceAvailability(DataSource dataSource) {
        try {
            Flyway.configure().dataSource(dataSource).load().migrate();
            return PersistenceAvailability.available();
        } catch (Exception exception) {
            return PersistenceAvailability.unavailable(exception.getMessage());
        }
    }
}
