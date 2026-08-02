package com.escontrela.lastmove.bootstrap;

import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenFactory;
import com.escontrela.lastmove.ui.service.ApplicationThemeService;
import javafx.stage.Stage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/** Declares JavaFX navigation beans after the primary stage becomes available. */
@Configuration
public class LastMoveUiConfiguration {

    @Bean
    @Lazy
    public UiScreenFactory uiScreenFactory(
            Stage primaryStage,
            SpringFxmlLoader fxmlLoader,
            ApplicationThemeService themeService) {
        return new UiScreenFactory(primaryStage, fxmlLoader, themeService);
    }

    @Bean
    @Lazy
    public UiFlowManager uiFlowManager(UiScreenFactory screenFactory) {
        return new UiFlowManager(screenFactory);
    }
}
