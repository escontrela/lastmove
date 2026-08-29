package com.escontrela.lastmove.infrastructure.config;

import com.escontrela.lastmove.domain.analysis.AnalysisDocumentFactory;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionFactory;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.domain.game.ChessRulesEngine;
import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.domain.service.ThreatenedSquaresService;
import com.escontrela.lastmove.domain.study.StudyChapterFactory;
import com.escontrela.lastmove.domain.tactics.TacticExerciseFactory;
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

    /** Builds independent analysis documents for sessions and persisted chapters. */
    @Bean
    public AnalysisDocumentFactory analysisDocumentFactory() {
        return new AnalysisDocumentFactory();
    }

    /** Creates analysis sessions from immutable records of played games. */
    @Bean
    public AnalysisSessionFactory analysisSessionFactory(AnalysisDocumentFactory documentFactory) {
        return new AnalysisSessionFactory(documentFactory);
    }

    /** Builds persisted study chapters from positions, PGN imports or session copies. */
    @Bean
    public StudyChapterFactory studyChapterFactory(AnalysisDocumentFactory documentFactory) {
        return new StudyChapterFactory(documentFactory);
    }

    /** Builds independent tactic exercises from positions or analysis documents. */
    @Bean
    public TacticExerciseFactory tacticExerciseFactory(AnalysisDocumentFactory documentFactory) {
        return new TacticExerciseFactory(documentFactory);
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
    @Bean public ThreatenedSquaresService threatenedSquaresService() { return new ThreatenedSquaresService(); }

    /** Supplies wall-clock time to progressive-game application services. */
    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }

}
