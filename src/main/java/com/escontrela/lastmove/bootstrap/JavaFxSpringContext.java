package com.escontrela.lastmove.bootstrap;

import java.util.Map;
import com.escontrela.lastmove.ui.service.DatabaseLocationPreferencesService;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * Provides controlled access to the Spring {@link ConfigurableApplicationContext}.
 *
 * <p>Keeps the Spring initialisation logic out of the JavaFX {@link javafx.application.Application}
 * class so that it can be replaced or tested independently.
 */
public final class JavaFxSpringContext {

    private JavaFxSpringContext() {}

    /**
     * Creates and returns a fully refreshed Spring application context.
     *
     * @param args command-line arguments forwarded from the launcher
     * @return the running Spring context
     */
    public static ConfigurableApplicationContext initialise(String[] args) {
        SpringApplication application = new SpringApplication(LastMoveApplication.class);
        application.addInitializers(context -> context.getEnvironment().getPropertySources()
                .addAfter("systemEnvironment", new MapPropertySource("lastmoveDatabaseLocation",
                        Map.of("spring.datasource.url",
                                DatabaseLocationPreferencesService.configuredJdbcUrl()))));
        return application.run(args);
    }
}
