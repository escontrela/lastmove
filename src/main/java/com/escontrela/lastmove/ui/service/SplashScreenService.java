package com.escontrela.lastmove.ui.service;

import com.escontrela.lastmove.ui.model.ApplicationThemeMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.springframework.stereotype.Component;

/** Displays the branded welcome screen before the primary workspace is opened. */
@Component
public class SplashScreenService {

  private static final Duration MINIMUM_DISPLAY_TIME = Duration.seconds(5);

  private static final double SPLASH_WIDTH = 680;
  private static final double SPLASH_HEIGHT = 460;

  private static final String LIGHT_BACKGROUND_RESOURCE = "/images/splash-light.png";
  private static final String DARK_BACKGROUND_RESOURCE = "/images/splash-dark.png";

  /*
   * ============================================================
   * LOADING SQUARES POSITION
   * ============================================================
   *
   * These values control ONLY the 3x3 animated indicator.
   *
   * Increase RIGHT_MARGIN -> moves the indicator to the LEFT.
   * Decrease RIGHT_MARGIN -> moves the indicator to the RIGHT.
   *
   * Increase TOP_MARGIN -> moves the indicator DOWN.
   * Decrease TOP_MARGIN -> moves the indicator UP.
   */
  private static final double SQUARES_RIGHT_MARGIN = 20;
  private static final double SQUARES_TOP_MARGIN = 120;

  /*
   * ============================================================
   * LOADING TEXT POSITION
   * ============================================================
   *
   * These values control ONLY "Starting workspace...".
   *
   * Increase RIGHT_MARGIN -> moves the text to the LEFT.
   * Decrease RIGHT_MARGIN -> moves the text to the RIGHT.
   *
   * Increase BOTTOM_MARGIN -> moves the text UP.
   * Decrease BOTTOM_MARGIN -> moves the text DOWN.
   */
  private static final double STATUS_RIGHT_MARGIN = 20;
  private static final double STATUS_BOTTOM_MARGIN = 0;

  /*
   * Loading indicator appearance.
   */
  private static final double LOADING_SQUARE_SIZE = 7;
  private static final double LOADING_SQUARE_GAP = 4;

  private static final String LOADING_MESSAGE = "Starting workspace...";

  private final StartupPreferencesService startupPreferencesService;
  private final ApplicationThemeService themeService;

  private Timeline loadingSquaresAnimation;
  private FadeTransition loadingTextAnimation;

  public SplashScreenService(
      StartupPreferencesService startupPreferencesService, ApplicationThemeService themeService) {

    this.startupPreferencesService = startupPreferencesService;
    this.themeService = themeService;
  }

  /** Shows the splash for at least three seconds, unless the saved preference disables it. */
  public void showIfEnabled(Runnable afterSplash) {

    Objects.requireNonNull(afterSplash, "afterSplash must not be null");

    if (!startupPreferencesService.isSplashScreenEnabled()) {
      afterSplash.run();
      return;
    }

    Stage splashStage = new Stage(StageStyle.UNDECORATED);

    Parent root = createRoot();

    themeService.register(root);

    Scene scene = new Scene(root, SPLASH_WIDTH, SPLASH_HEIGHT);

    scene
        .getStylesheets()
        .add(
            Objects.requireNonNull(
                    getClass().getResource("/css/lastmove.css"),
                    "Missing stylesheet: /css/lastmove.css")
                .toExternalForm());

    splashStage.setScene(scene);
    splashStage.setResizable(false);
    splashStage.centerOnScreen();

    splashStage.setOnHidden(event -> stopLoadingAnimations());

    splashStage.show();

    PauseTransition pause = new PauseTransition(MINIMUM_DISPLAY_TIME);

    pause.setOnFinished(
        event -> {
          stopLoadingAnimations();
          splashStage.close();
          afterSplash.run();
        });

    pause.play();
  }

  private Parent createRoot() {

    /*
     * ------------------------------------------------------------
     * Background
     * ------------------------------------------------------------
     */
    Image background = loadBackground();

    ImageView backgroundView = new ImageView(background);

    backgroundView.setFitWidth(SPLASH_WIDTH);
    backgroundView.setFitHeight(SPLASH_HEIGHT);
    backgroundView.setPreserveRatio(false);
    backgroundView.setSmooth(true);
    backgroundView.setMouseTransparent(true);

    /*
     * ------------------------------------------------------------
     * Animated squares
     * ------------------------------------------------------------
     */
    List<Rectangle> loadingSquares = new ArrayList<>();

    GridPane loadingSquaresGrid = createLoadingSquares(loadingSquares);

    /*
     * Keep its dimensions fixed.
     */
    loadingSquaresGrid.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

    loadingSquaresGrid.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

    /*
     * ------------------------------------------------------------
     * Loading text
     * ------------------------------------------------------------
     */
    Label loadingStatus = new Label(LOADING_MESSAGE);

    loadingStatus.getStyleClass().add("splash-loading-status");

    loadingStatus.setMouseTransparent(true);

    /*
     * Prevent any layout movement.
     */
    loadingStatus.setMinWidth(Region.USE_PREF_SIZE);

    loadingStatus.setPrefWidth(Region.USE_COMPUTED_SIZE);

    loadingStatus.setMaxWidth(Region.USE_PREF_SIZE);

    /*
     * ------------------------------------------------------------
     * Overlay
     * ------------------------------------------------------------
     *
     * IMPORTANT:
     *
     * The squares and text are two completely independent children.
     * They no longer share an HBox/VBox.
     *
     * This means moving or animating one cannot affect the other.
     */
    AnchorPane overlay = new AnchorPane();

    overlay.setMouseTransparent(true);
    overlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

    overlay.getChildren().addAll(loadingSquaresGrid, loadingStatus);

    /*
     * ------------------------------------------------------------
     * 3x3 indicator position
     * ------------------------------------------------------------
     *
     * Intended location:
     * upper-right area, below the "Chess" word.
     */
    AnchorPane.setRightAnchor(loadingSquaresGrid, SQUARES_RIGHT_MARGIN);

    AnchorPane.setTopAnchor(loadingSquaresGrid, SQUARES_TOP_MARGIN);

    /*
     * ------------------------------------------------------------
     * Status text position
     * ------------------------------------------------------------
     *
     * Intended location:
     * bottom-right corner.
     */
    AnchorPane.setRightAnchor(loadingStatus, STATUS_RIGHT_MARGIN);

    AnchorPane.setBottomAnchor(loadingStatus, STATUS_BOTTOM_MARGIN);

    /*
     * ------------------------------------------------------------
     * Root
     * ------------------------------------------------------------
     */
    StackPane root = new StackPane(backgroundView, overlay);

    root.getStyleClass().add("splash-shell");

    startLoadingSquaresAnimation(loadingSquares);

    startLoadingTextAnimation(loadingStatus);

    return root;
  }

  /**
   * Creates the 3x3 animated loading indicator.
   *
   * <pre>
   * ■ ■ ■
   * ■ ■ ■
   * ■ ■ ■
   * </pre>
   */
  private GridPane createLoadingSquares(List<Rectangle> squares) {

    GridPane grid = new GridPane();

    grid.setHgap(LOADING_SQUARE_GAP);

    grid.setVgap(LOADING_SQUARE_GAP);

    grid.setAlignment(Pos.CENTER);

    grid.setMouseTransparent(true);

    boolean nightMode = themeService.currentThemeMode() == ApplicationThemeMode.NIGHT;

    Color inactiveColor = nightMode ? Color.web("#195A91") : Color.web("#8BBDF2");

    for (int index = 0; index < 9; index++) {

      Rectangle square = new Rectangle(LOADING_SQUARE_SIZE, LOADING_SQUARE_SIZE);

      square.setArcWidth(3);
      square.setArcHeight(3);

      square.setFill(inactiveColor);

      square.setOpacity(0.35);

      int row = index / 3;

      int column = index % 3;

      grid.add(square, column, row);

      squares.add(square);
    }

    return grid;
  }

  /**
   * Soft blink for "Starting workspace...".
   *
   * <p>The text never changes, so its position remains fixed.
   */
  private void startLoadingTextAnimation(Label loadingStatus) {

    stopLoadingTextAnimation();

    loadingStatus.setOpacity(1.0);

    loadingTextAnimation = new FadeTransition(Duration.millis(1100), loadingStatus);

    loadingTextAnimation.setFromValue(1.0);
    loadingTextAnimation.setToValue(0.60);

    loadingTextAnimation.setAutoReverse(true);

    loadingTextAnimation.setCycleCount(FadeTransition.INDEFINITE);

    loadingTextAnimation.play();
  }

  /**
   * Creates a travelling highlight through the 3x3 indicator.
   *
   * <p>Only color and opacity change. The squares never move or resize.
   */
  private void startLoadingSquaresAnimation(List<Rectangle> squares) {

    stopLoadingSquaresAnimation();

    if (squares.isEmpty()) {
      return;
    }

    boolean nightMode = themeService.currentThemeMode() == ApplicationThemeMode.NIGHT;

    Color activeColor = nightMode ? Color.web("#1683FF") : Color.web("#0878E5");

    Color inactiveColor = nightMode ? Color.web("#195A91") : Color.web("#8BBDF2");

    loadingSquaresAnimation = new Timeline();

    for (int index = 0; index < squares.size(); index++) {

      int activeIndex = index;

      KeyFrame frame =
          new KeyFrame(
              Duration.millis(index * 110L),
              event -> {
                for (int squareIndex = 0; squareIndex < squares.size(); squareIndex++) {

                  Rectangle square = squares.get(squareIndex);

                  if (squareIndex == activeIndex) {

                    square.setFill(activeColor);

                    square.setOpacity(1.0);

                  } else {

                    int distance = Math.abs(squareIndex - activeIndex);

                    double opacity =
                        switch (distance) {
                          case 1 -> 0.65;
                          case 2 -> 0.45;
                          default -> 0.28;
                        };

                    square.setFill(inactiveColor);

                    square.setOpacity(opacity);
                  }
                }
              });

      loadingSquaresAnimation.getKeyFrames().add(frame);
    }

    /*
     * Small pause/reset before restarting.
     */
    loadingSquaresAnimation
        .getKeyFrames()
        .add(
            new KeyFrame(
                Duration.millis(squares.size() * 110L + 150),
                event -> {
                  for (Rectangle square : squares) {

                    square.setFill(inactiveColor);

                    square.setOpacity(0.35);
                  }
                }));

    loadingSquaresAnimation.setCycleCount(Timeline.INDEFINITE);

    loadingSquaresAnimation.play();
  }

  private void stopLoadingAnimations() {

    stopLoadingTextAnimation();
    stopLoadingSquaresAnimation();
  }

  private void stopLoadingTextAnimation() {

    if (loadingTextAnimation != null) {

      loadingTextAnimation.stop();
      loadingTextAnimation = null;
    }
  }

  private void stopLoadingSquaresAnimation() {

    if (loadingSquaresAnimation != null) {

      loadingSquaresAnimation.stop();
      loadingSquaresAnimation = null;
    }
  }

  private Image loadBackground() {

    String resource =
        themeService.currentThemeMode() == ApplicationThemeMode.NIGHT
            ? DARK_BACKGROUND_RESOURCE
            : LIGHT_BACKGROUND_RESOURCE;

    return new Image(
        Objects.requireNonNull(
                getClass().getResource(resource),
                () -> "Missing splash background resource: " + resource)
            .toExternalForm());
  }
}
