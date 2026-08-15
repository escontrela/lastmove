package com.escontrela.lastmove.infrastructure.config;

import com.escontrela.lastmove.domain.analysis.AnalysisSessionFactory;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.domain.game.ChessRulesEngine;
import com.escontrela.lastmove.domain.service.FenService;
import java.time.Clock;
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

    /** Creates analysis sessions from immutable records of played games. */
    @Bean
    public AnalysisSessionFactory analysisSessionFactory() {
        return new AnalysisSessionFactory();
    }

    /** Creates domain chess games with the configured rules-engine implementation. */
    @Bean
    public ChessGameFactory chessGameFactory(ChessRulesEngine rulesEngine) {
        return new ChessGameFactory(rulesEngine);
    }

    @Bean
    public FenService fenService() {
        return new FenService();
    }

    /** Supplies wall-clock time to progressive-game application services. */
    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }

}
