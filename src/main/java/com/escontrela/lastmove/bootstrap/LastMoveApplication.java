package com.escontrela.lastmove.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot configuration root for LastMove.
 *
 * <p>LastMove uses Spring Boot only as a dependency-injection container.
 * No web server is started; see {@code application.properties}.
 */
@SpringBootApplication(scanBasePackages = "com.escontrela.lastmove")
public class LastMoveApplication {

    public static void run(String[] args) {
        SpringApplication.run(LastMoveApplication.class, args);
    }
}
