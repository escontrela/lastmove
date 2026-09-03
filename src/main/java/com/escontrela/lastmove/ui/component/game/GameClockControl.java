package com.escontrela.lastmove.ui.component.game;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Reusable chess clock with a seven-segment display and remaining-time indicator.
 *
 * <p>The control only renders UI state. Game timing remains owned by the application service.
 */
public final class GameClockControl extends VBox {

  private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
  private static final PseudoClass LOW_TIME = PseudoClass.getPseudoClass("low-time");
  private static final int[][] DIGIT_SEGMENTS = {
      {0, 1, 2, 3, 4, 5},
      {1, 2},
      {0, 1, 6, 4, 3},
      {0, 1, 6, 2, 3},
      {5, 6, 1, 2},
      {0, 5, 6, 2, 3},
      {0, 5, 6, 4, 2, 3},
      {0, 1, 2},
      {0, 1, 2, 3, 4, 5, 6},
      {0, 1, 2, 3, 5, 6}
  };

  private final HBox progressTrack = new HBox();
  private final Region remainingFill = new Region();
  private final Region elapsedFill = new Region();
  private final HBox display = new HBox(4);
  private final SevenSegmentDigit minuteTens = new SevenSegmentDigit();
  private final SevenSegmentDigit minuteUnits = new SevenSegmentDigit();
  private final SevenSegmentDigit secondTens = new SevenSegmentDigit();
  private final SevenSegmentDigit secondUnits = new SevenSegmentDigit();
  private final VBox colon = new VBox(10);
  private final Label unlimited = new Label("∞");
  private final Timeline colonBlink;
  private boolean colonVisible = true;
  private boolean active;
  private boolean timed;

  public GameClockControl() {
    getStyleClass().add("game-clock");
    setAlignment(Pos.CENTER);
    setSpacing(10);
    setAccessibleRole(AccessibleRole.TEXT);

    progressTrack.getStyleClass().add("game-clock-progress-track");
    remainingFill.getStyleClass().add("game-clock-progress-remaining");
    elapsedFill.getStyleClass().add("game-clock-progress-elapsed");
    remainingFill.prefHeightProperty().bind(progressTrack.heightProperty());
    elapsedFill.prefHeightProperty().bind(progressTrack.heightProperty());
    progressTrack.getChildren().setAll(remainingFill, elapsedFill);

    Region upperDot = colonDot();
    Region lowerDot = colonDot();
    colon.getStyleClass().add("game-clock-colon");
    colon.setAlignment(Pos.CENTER);
    colon.getChildren().setAll(upperDot, lowerDot);

    display.getStyleClass().add("game-clock-display");
    display.setAlignment(Pos.CENTER);
    display.getChildren().setAll(
        minuteTens, minuteUnits, colon, secondTens, secondUnits);

    unlimited.getStyleClass().add("game-clock-unlimited");
    unlimited.setVisible(false);
    unlimited.setManaged(false);

    getChildren().setAll(progressTrack, display, unlimited);

    colonBlink = new Timeline(new KeyFrame(javafx.util.Duration.millis(500), event -> blinkColon()));
    colonBlink.setCycleCount(Animation.INDEFINITE);
    sceneProperty().addListener((ignored, oldScene, newScene) -> updateBlinking());
    clear();
  }

  /** Shows an unset clock while the game configuration is not available yet. */
  public void clear() {
    timed = false;
    display.setVisible(true);
    display.setManaged(true);
    unlimited.setVisible(false);
    unlimited.setManaged(false);
    minuteTens.showDash();
    minuteUnits.showDash();
    secondTens.showDash();
    secondUnits.showDash();
    progressTrack.setVisible(false);
    pseudoClassStateChanged(LOW_TIME, false);
    setAccessibleText("Clock not set");
    updateBlinking();
  }

  /** Renders the authoritative remaining time and its configured initial duration. */
  public void setTime(Optional<Duration> remaining, Optional<Duration> initial) {
    Objects.requireNonNull(remaining, "remaining must not be null");
    Objects.requireNonNull(initial, "initial must not be null");
    timed = remaining.isPresent();
    display.setVisible(timed);
    display.setManaged(timed);
    unlimited.setVisible(!timed);
    unlimited.setManaged(!timed);

    if (!timed) {
      progressTrack.setVisible(false);
      setAccessibleText("No time limit");
      updateBlinking();
      return;
    }

    Duration value = remaining.orElseThrow();
    long seconds = Math.max(0, value.toSeconds());
    long minutes = Math.min(99, seconds / 60);
    int secondsPart = (int) (seconds % 60);
    minuteTens.show((int) (minutes / 10));
    minuteUnits.show((int) (minutes % 10));
    secondTens.show(secondsPart / 10);
    secondUnits.show(secondsPart % 10);

    double fraction = remainingFraction(value, initial.orElse(value));
    progressTrack.setVisible(true);
    remainingFill.prefWidthProperty().unbind();
    elapsedFill.prefWidthProperty().unbind();
    remainingFill.prefWidthProperty().bind(progressTrack.widthProperty().multiply(fraction));
    elapsedFill.prefWidthProperty().bind(progressTrack.widthProperty().multiply(1 - fraction));
    pseudoClassStateChanged(LOW_TIME, fraction <= 0.20);
    setAccessibleText("%02d:%02d remaining".formatted(minutes, secondsPart));
    updateBlinking();
  }

  /** Highlights the clock whose side is currently running. */
  public void setActive(boolean active) {
    this.active = active;
    pseudoClassStateChanged(ACTIVE, active);
    updateBlinking();
  }

  static double remainingFraction(Duration remaining, Duration initial) {
    Objects.requireNonNull(remaining);
    Objects.requireNonNull(initial);
    if (initial.isZero() || initial.isNegative()) {
      return 0;
    }
    return Math.clamp((double) remaining.toMillis() / initial.toMillis(), 0, 1);
  }

  private Region colonDot() {
    Region dot = new Region();
    dot.getStyleClass().add("game-clock-colon-dot");
    return dot;
  }

  private void updateBlinking() {
    if (getScene() != null && active && timed) {
      if (colonBlink.getStatus() != Animation.Status.RUNNING) {
        colonVisible = true;
        colon.setOpacity(1);
        colonBlink.play();
      }
    } else {
      colonBlink.stop();
      colonVisible = true;
      colon.setOpacity(1);
    }
  }

  private void blinkColon() {
    colonVisible = !colonVisible;
    colon.setOpacity(colonVisible ? 1 : 0.14);
  }

  private static final class SevenSegmentDigit extends Pane {
    private final List<Region> segments;

    private SevenSegmentDigit() {
      getStyleClass().add("game-clock-digit");
      setMinSize(23, 44);
      setPrefSize(23, 44);
      setMaxSize(23, 44);
      segments = java.util.stream.IntStream.range(0, 7)
          .mapToObj(index -> segment(index, index == 0 || index == 3 || index == 6))
          .toList();
      getChildren().setAll(segments);
    }

    private void show(int value) {
      List<Integer> illuminated = java.util.Arrays.stream(DIGIT_SEGMENTS[value]).boxed().toList();
      for (int index = 0; index < segments.size(); index++) {
        Region segment = segments.get(index);
        if (illuminated.contains(index)) {
          if (!segment.getStyleClass().contains("on")) {
            segment.getStyleClass().add("on");
          }
        } else {
          segment.getStyleClass().remove("on");
        }
      }
    }

    private void showDash() {
      for (int index = 0; index < segments.size(); index++) {
        Region segment = segments.get(index);
        if (index == 6) {
          if (!segment.getStyleClass().contains("on")) {
            segment.getStyleClass().add("on");
          }
        } else {
          segment.getStyleClass().remove("on");
        }
      }
    }

    private Region segment(int index, boolean horizontal) {
      Region segment = new Region();
      segment.getStyleClass().add("game-clock-segment");
      segment.setPrefSize(horizontal ? 15 : 4, horizontal ? 4 : 17);
      double[][] coordinates = {
          {4, 0}, {19, 3}, {19, 24}, {4, 40}, {0, 24}, {0, 3}, {4, 20}
      };
      segment.relocate(coordinates[index][0], coordinates[index][1]);
      return segment;
    }
  }
}
