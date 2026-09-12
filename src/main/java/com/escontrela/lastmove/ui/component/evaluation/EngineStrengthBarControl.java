package com.escontrela.lastmove.ui.component.evaluation;

import com.escontrela.lastmove.application.dto.EngineEvaluationState;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Compact, presentation-only evaluation indicator for a board position.
 *
 * <p>The supplied score is already normalized to White's perspective by the application layer.
 * This control deliberately exposes neither engine controls nor a suggested move.
 */
public final class EngineStrengthBarControl extends HBox {

  private static final double MAX_DISPLAY_ADVANTAGE = 10.0;
  private final Label score = new Label("—");
  private final Pane whiteAdvantage = new Pane();
  private final StackPane track = new StackPane();
  private final DoubleProperty displayedWhiteShare =
      new SimpleDoubleProperty(this, "displayedWhiteShare", 0.5);
  private Timeline strengthAnimation;

  public EngineStrengthBarControl() {
    getStyleClass().add("engine-strength-bar");
    setAlignment(Pos.CENTER);
    setManaged(false);
    setMouseTransparent(true);

    score.getStyleClass().add("engine-strength-score");
    VBox scoreHost = new VBox(score);
    scoreHost.getStyleClass().add("engine-strength-score-host");
    scoreHost.setAlignment(Pos.CENTER);

    Pane blackAdvantage = new Pane();
    blackAdvantage.getStyleClass().add("engine-strength-black");
    whiteAdvantage.getStyleClass().add("engine-strength-white");
    whiteAdvantage.setManaged(false);
    whiteAdvantage.setMouseTransparent(true);
    track.getChildren().addAll(blackAdvantage, whiteAdvantage);
    track.getStyleClass().add("engine-strength-track");
    track.setMinWidth(12);
    track.setPrefWidth(12);
    track.setMaxWidth(12);
    whiteAdvantage.prefWidthProperty().bind(track.widthProperty());
    whiteAdvantage.maxWidthProperty().bind(track.widthProperty());
    displayedWhiteShare.addListener(
        (ignored, oldShare, newShare) -> layoutWhiteAdvantage(newShare.doubleValue()));
    track.heightProperty().addListener(
        (ignored, oldHeight, newHeight) -> layoutWhiteAdvantage(displayedWhiteShare.get()));

    getChildren().addAll(scoreHost, track);
    render(EngineEvaluationState.idle(
        new com.escontrela.lastmove.application.computer.ComputerEngineDescriptor("pending", "Engine", "—")));
  }

  /** Renders the same engine-neutral state used by the full evaluation card. */
  public void render(EngineEvaluationState state) {
    EngineEvaluationState required = Objects.requireNonNull(state, "state");
    Optional<String> value = required.score();
    score.setText(value.map(EngineStrengthBarControl::compactScore).orElse(required.searching() ? "…" : "—"));
    if (value.isPresent()) {
      animateTo(value.map(EngineStrengthBarControl::whiteShare).orElseThrow());
    } else if (!required.searching()) {
      animateTo(0.5);
    }
  }

  static double whiteShare(String scoreText) {
    String value = scoreText.trim();
    if (value.startsWith("#")) return 1.0;
    if (value.startsWith("-#")) return 0.0;
    try {
      double advantage = Double.parseDouble(value);
      double normalized = Math.max(-MAX_DISPLAY_ADVANTAGE, Math.min(MAX_DISPLAY_ADVANTAGE, advantage));
      return 0.5 + (normalized / MAX_DISPLAY_ADVANTAGE) * 0.45;
    } catch (NumberFormatException ignored) {
      return 0.5;
    }
  }

  private static String compactScore(String scoreText) {
    if (scoreText.startsWith("#") || scoreText.startsWith("-#")) return scoreText;
    try {
      return String.format(Locale.ROOT, "%+.1f", Double.parseDouble(scoreText));
    } catch (NumberFormatException ignored) {
      return scoreText;
    }
  }

  private void animateTo(double targetShare) {
    if (strengthAnimation != null) strengthAnimation.stop();
    double distance = Math.abs(targetShare - displayedWhiteShare.get());
    if (distance < 0.0001) {
      layoutWhiteAdvantage(targetShare);
      return;
    }
    double durationMillis = 260 + distance * 420;
    strengthAnimation =
        new Timeline(
            new KeyFrame(
                javafx.util.Duration.millis(durationMillis),
                new KeyValue(displayedWhiteShare, targetShare, Interpolator.EASE_BOTH)));
    strengthAnimation.play();
  }

  private void layoutWhiteAdvantage(double share) {
    double height = track.getHeight();
    if (height <= 0) return;
    whiteAdvantage.resizeRelocate(0, height * (1 - share), track.getWidth(), height * share);
  }
}
