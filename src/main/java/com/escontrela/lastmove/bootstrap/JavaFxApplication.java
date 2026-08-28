package com.escontrela.lastmove.bootstrap;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.image.Image;
import java.util.Objects;
import org.springframework.context.ConfigurableApplicationContext;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import com.escontrela.lastmove.ui.service.SplashScreenService;

/**
 * JavaFX {@link Application} lifecycle integration for LastMove.
 *
 * <p>Initialises the Spring context during {@link #init()} and delegates primary-window
 * navigation to {@link UiFlowManager} during {@link #start(Stage)}.
 */
public class JavaFxApplication extends Application {

    private ConfigurableApplicationContext springContext;

    @Override
    public void init() {
        springContext = JavaFxSpringContext.initialise(getParameters().getRaw().toArray(new String[0]));
    }

    @Override
    public void start(Stage primaryStage) {
        springContext.getBeanFactory().registerSingleton("primaryStage", primaryStage);
        primaryStage.setTitle("LastMove");
        primaryStage.getIcons().add(
                new Image(Objects.requireNonNull(
                        JavaFxApplication.class.getResource("/images/lastmove-knight-mark.png"))
                        .toExternalForm()));
        UiFlowManager uiFlowManager = springContext.getBean(UiFlowManager.class);
        springContext.getBean(SplashScreenService.class)
                .showIfEnabled(() -> uiFlowManager.show(UiScreenId.MAIN));
    }

    @Override
    public void stop() {
        springContext.close();
    }

}
