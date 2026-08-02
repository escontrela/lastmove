package com.escontrela.lastmove.bootstrap;

import javafx.application.Application;
import javafx.stage.Stage;
import org.springframework.context.ConfigurableApplicationContext;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenId;

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
        springContext.getBean(UiFlowManager.class).show(UiScreenId.MAIN);
    }

    @Override
    public void stop() {
        springContext.close();
    }
}
