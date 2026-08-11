package com.escontrela.lastmove.ui.service;

import com.escontrela.lastmove.ui.model.ApplicationThemeMode;
import java.util.Objects;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.springframework.stereotype.Component;

/** Displays the branded welcome screen before the primary workspace is opened. */
@Component
public class SplashScreenService {

    private static final Duration MINIMUM_DISPLAY_TIME = Duration.seconds(3);
    private static final String LIGHT_LOGO_RESOURCE = "/images/lastmove-chess-logo.png";
    private static final String DARK_LOGO_RESOURCE = "/images/lastmove-chess-logo-dark.png";

    private final StartupPreferencesService startupPreferencesService;
    private final ApplicationThemeService themeService;

    public SplashScreenService(
            StartupPreferencesService startupPreferencesService, ApplicationThemeService themeService) {
        this.startupPreferencesService = startupPreferencesService;
        this.themeService = themeService;
    }

    /** Shows the splash for at least three seconds, unless the saved preference disables it. */
    public void showIfEnabled(Runnable afterSplash) {
        if (!startupPreferencesService.isSplashScreenEnabled()) {
            afterSplash.run();
            return;
        }

        Stage splashStage = new Stage(StageStyle.UNDECORATED);
        Parent root = createRoot();
        themeService.register(root);

        Scene scene = new Scene(root, 680, 380);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/css/lastmove.css"),
                "Missing stylesheet: /css/lastmove.css").toExternalForm());
        splashStage.setScene(scene);
        splashStage.setResizable(false);
        splashStage.centerOnScreen();
        splashStage.show();

        PauseTransition pause = new PauseTransition(MINIMUM_DISPLAY_TIME);
        pause.setOnFinished(event -> {
            splashStage.close();
            afterSplash.run();
        });
        pause.play();
    }

    private Parent createRoot() {
        ImageView logo = new ImageView(loadLogo());
        logo.setFitWidth(460);
        logo.setFitHeight(205);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);

        Label tagline = new Label("Your chess workspace");
        tagline.getStyleClass().add("splash-tagline");

        Label loadingStatus = new Label();
        loadingStatus.getStyleClass().add("splash-loading-status");
        startLoadingAnimation(loadingStatus);

        GridPane loadingGrid = createLoadingGrid();

        VBox root = new VBox(16, logo, tagline, loadingGrid, loadingStatus);
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().add("splash-shell");
        return root;
    }

    private GridPane createLoadingGrid() {
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(6);
        grid.setVgap(6);
        for (int index = 0; index < 9; index++) {
            Region cell = new Region();
            cell.getStyleClass().add("splash-loader-cell");
            grid.add(cell, index % 3, index / 3);

            FadeTransition pulse = new FadeTransition(Duration.millis(780), cell);
            pulse.setFromValue(0.2);
            pulse.setToValue(1.0);
            pulse.setAutoReverse(true);
            pulse.setCycleCount(Timeline.INDEFINITE);
            pulse.setDelay(Duration.millis(index * 70L));
            pulse.play();
        }
        return grid;
    }

    private void startLoadingAnimation(Label loadingStatus) {
        String message = "Starting workspace...";
        Timeline typing = new Timeline();
        for (int index = 0; index <= message.length(); index++) {
            int visibleCharacters = index;
            typing.getKeyFrames().add(new javafx.animation.KeyFrame(
                    Duration.millis(index * 52L),
                    event -> loadingStatus.setText(message.substring(0, visibleCharacters))));
        }
        typing.play();
    }

    private Image loadLogo() {
        String resource = themeService.currentThemeMode() == ApplicationThemeMode.NIGHT
                ? DARK_LOGO_RESOURCE
                : LIGHT_LOGO_RESOURCE;
        return new Image(Objects.requireNonNull(
                getClass().getResource(resource),
                () -> "Missing splash logo resource: " + resource).toExternalForm());
    }
}
