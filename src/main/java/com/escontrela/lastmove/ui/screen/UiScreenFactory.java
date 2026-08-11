package com.escontrela.lastmove.ui.screen;

import com.escontrela.lastmove.bootstrap.SpringFxmlLoader;
import com.escontrela.lastmove.ui.service.ApplicationThemeService;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
/** Creates and swaps Spring-managed FXML views in the primary stage. */
public class UiScreenFactory {

    private static final double INITIAL_X = 87.0;
    private static final double INITIAL_Y = 42.0;
    private static final double MIN_WIDTH = 1100.0;
    private static final double MIN_HEIGHT = 720.0;

    private final Stage primaryStage;
    private final SpringFxmlLoader fxmlLoader;
    private final ApplicationThemeService themeService;

    public UiScreenFactory(
            Stage primaryStage, SpringFxmlLoader fxmlLoader, ApplicationThemeService themeService) {
        this.primaryStage = primaryStage;
        this.fxmlLoader = fxmlLoader;
        this.themeService = themeService;
    }

    public UiScreen create(UiScreenId screenId) {
        Parent root = fxmlLoader.load(screenId.fxmlPath());
        themeService.register(root);
        UiScreenController controller = (UiScreenController) root.getProperties().get("controller");
        if (controller == null) {
            throw new IllegalStateException("Screen controller was not registered for " + screenId);
        }

        Scene scene = primaryStage.getScene();
        if (scene == null) {
            scene = new Scene(root, screenId.width(), screenId.height());
            primaryStage.setX(INITIAL_X);
            primaryStage.setY(INITIAL_Y);
            primaryStage.setWidth(screenId.width());
            primaryStage.setHeight(screenId.height());
            primaryStage.setScene(scene);
        } else {
            scene.setRoot(root);
        }

        String stylesheet = UiScreenFactory.class.getResource("/css/lastmove.css").toExternalForm();
        if (!scene.getStylesheets().contains(stylesheet)) {
            scene.getStylesheets().add(stylesheet);
        }
        primaryStage.setMinWidth(MIN_WIDTH);
        primaryStage.setMinHeight(MIN_HEIGHT);
        return new StageUiScreen(screenId, primaryStage, scene, controller);
    }
}
