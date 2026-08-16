package com.escontrela.lastmove.ui.component.game;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.util.Duration;

/**
 * Status label that can reveal a short message progressively and blink a block cursor briefly.
 *
 * <p>The animation is presentation-only. Replacing the message or leaving the human turn stops all
 * timelines immediately, so periodic game-state refreshes cannot accumulate animations.
 */
public final class TypewriterStatusLabel extends Label {

  private static final String CURSOR = "█";
  private static final Duration CHARACTER_DELAY = Duration.millis(65);
  private static final Duration CURSOR_BLINK = Duration.millis(360);
  private static final Duration CURSOR_LIFETIME = Duration.seconds(1);

  private final Timeline typing = new Timeline();
  private final Timeline cursor = new Timeline();
  private final PauseTransition cursorTimeout = new PauseTransition(CURSOR_LIFETIME);
  private String completeText = "";

  public TypewriterStatusLabel() {
    getStyleClass().add("typewriter-status-label");
    cursor.setCycleCount(Timeline.INDEFINITE);
    cursor.getKeyFrames().setAll(
        new KeyFrame(Duration.ZERO, event -> setText(completeText + CURSOR)),
        new KeyFrame(CURSOR_BLINK, event -> setText(completeText)),
        new KeyFrame(CURSOR_BLINK.multiply(2), event -> setText(completeText + CURSOR)));
    cursorTimeout.setOnFinished(event -> stopCursor());
  }

  /** Types the supplied message once, then blinks its block cursor for a bounded time. */
  public void play(String message) {
    stopAnimations();
    completeText = message == null ? "" : message;
    setAccessibleText(completeText);
    setText(CURSOR);
    for (int index = 1; index <= completeText.length(); index++) {
      int visibleCharacters = index;
      typing.getKeyFrames().add(
          new KeyFrame(
              CHARACTER_DELAY.multiply(index),
              event -> setText(completeText.substring(0, visibleCharacters) + CURSOR)));
    }
    typing.setOnFinished(
        event -> {
          cursor.playFromStart();
          cursorTimeout.playFromStart();
        });
    typing.playFromStart();
  }

  /** Cancels any animation and displays a stable message without a cursor. */
  public void showImmediately(String message) {
    stopAnimations();
    completeText = message == null ? "" : message;
    setAccessibleText(completeText);
    setText(completeText);
  }

  private void stopAnimations() {
    typing.stop();
    typing.getKeyFrames().clear();
    cursor.stop();
    cursorTimeout.stop();
  }

  private void stopCursor() {
    cursor.stop();
    setText(completeText);
  }
}
