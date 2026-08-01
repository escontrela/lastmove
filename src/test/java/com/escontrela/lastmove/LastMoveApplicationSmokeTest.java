package com.escontrela.lastmove;

import com.escontrela.lastmove.bootstrap.LastMoveApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test that verifies the Spring application context loads successfully
 * without starting a JavaFX window.
 */
@SpringBootTest(classes = LastMoveApplication.class)
@TestPropertySource(properties = {
        "spring.main.web-application-type=none"
})
class LastMoveApplicationSmokeTest {

    @Test
    void contextLoads() {
        // The Spring context must load without throwing any exceptions.
    }
}
