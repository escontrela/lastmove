package com.escontrela.lastmove.ui.component.game;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * Compact single-row activity indicator for a player whose move is being calculated.
 *
 * <p>The three cells reuse the splash-screen pulse language without imposing screen-specific
 * engine or game state on the control. The owning controller starts and stops it explicitly.
 */
public final class ThinkingIndicatorControl extends HBox {

  private static final int CELL_COUNT = 3;
  private static final Duration PULSE_DURATION = Duration.millis(720);
  private static final Duration PULSE_DELAY = Duration.millis(110);

  private final ParallelTransition animation = new ParallelTransition();

  public ThinkingIndicatorControl() {
    setAlignment(Pos.CENTER_RIGHT);
    setSpacing(4.0);
    setAccessibleText("Computer is thinking");
    getStyleClass().add("computer-thinking-indicator");
    for (int index = 0; index < CELL_COUNT; index++) {
      Region cell = new Region();
      cell.getStyleClass().add("computer-thinking-dot");
      getChildren().add(cell);
      FadeTransition pulse = new FadeTransition(PULSE_DURATION, cell);
      pulse.setFromValue(0.22);
      pulse.setToValue(1.0);
      pulse.setAutoReverse(true);
      pulse.setCycleCount(Timeline.INDEFINITE);
      pulse.setDelay(PULSE_DELAY.multiply(index));
      animation.getChildren().add(pulse);
    }
    setThinking(false);
  }

  /** Shows and animates the row while thinking, or hides and resets it when idle. */
  public void setThinking(boolean thinking) {
    setVisible(thinking);
    setManaged(thinking);
    if (thinking) {
      animation.play();
    } else {
      animation.stop();
      getChildren().forEach(cell -> cell.setOpacity(1.0));
    }
  }
}
