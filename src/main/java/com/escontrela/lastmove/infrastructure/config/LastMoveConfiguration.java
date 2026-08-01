package com.escontrela.lastmove.infrastructure.config;

import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.domain.service.GameNavigationService;
import com.escontrela.lastmove.domain.service.PgnService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for LastMove beans.
 *
 * <p>Declares domain services and any infrastructure beans that cannot use
 * {@code @Component} directly.
 */
@Configuration
public class LastMoveConfiguration {

    @Bean
    public FenService fenService() {
        return new FenService();
    }

    @Bean
    public PgnService pgnService() {
        return new PgnService();
    }

    @Bean
    public GameNavigationService gameNavigationService() {
        return new GameNavigationService();
    }
}
