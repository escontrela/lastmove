package com.escontrela.lastmove.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

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
        return SpringApplication.run(LastMoveApplication.class, args);
    }
}
