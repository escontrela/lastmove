package com.escontrela.lastmove.ui.component.profile;

import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Objects;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

/** Small reusable player avatar: photo, Knightshade mark, or a two-letter monogram. */
public final class PlayerAvatarControl extends StackPane {
  private static final int TONE_COUNT = 5;
  private final Circle outerRing = new Circle(14);
  private final Circle face = new Circle(11.5);
  private final Label initials = new Label();
  private final ImageView image = new ImageView();

  public PlayerAvatarControl() {
    getStyleClass().add("player-avatar");
    outerRing.getStyleClass().add("player-avatar-ring");
    face.getStyleClass().add("player-avatar-face");
    initials.getStyleClass().add("player-avatar-initials");
    image.setFitWidth(28);
    image.setFitHeight(28);
    image.setPreserveRatio(true);
    image.setClip(new Circle(14, 14, 14));
    setMinSize(28, 28);
    setPrefSize(28, 28);
    setMaxSize(28, 28);
    getChildren().setAll(outerRing, face, initials);
  }

  public void showInitials(String name) {
    String value = Objects.requireNonNullElse(name, "").trim();
    initials.setText(initialsFor(value));
    initials.setVisible(true);
    getChildren().setAll(outerRing, face, initials);
    tone(value);
    installTooltip(value.isBlank() ? "Unknown player" : value);
  }

  public void showPhoto(byte[] photo, String name) {
    Objects.requireNonNull(photo, "photo must not be null");
    image.setImage(new Image(new ByteArrayInputStream(photo)));
    image.setClip(new Circle(14, 14, 14));
    getChildren().setAll(outerRing, image);
    installTooltip(Objects.requireNonNullElse(name, "Player"));
  }

  public void showKnightshade(String name) {
    image.setImage(new Image(Objects.requireNonNull(getClass().getResource(
        "/images/knightshade-engine-mark.png")).toExternalForm()));
    image.setClip(null);
    getChildren().setAll(outerRing, image);
    installTooltip(Objects.requireNonNullElse(name, "Knightshade"));
  }

  public static String initialsFor(String name) {
    String value = Objects.requireNonNullElse(name, "").trim();
    if (value.isBlank() || "unknown".equalsIgnoreCase(value)) return "?";
    String[] words = value.split("\\s+");
    if (words.length == 1) {
      int first = value.codePointAt(0);
      int second = value.offsetByCodePoints(0, 1) < value.length()
          ? value.codePointAt(value.offsetByCodePoints(0, 1)) : first;
      return (new String(Character.toChars(first)) + new String(Character.toChars(second))).toUpperCase(Locale.ROOT);
    }
    return (firstCharacter(words[0]) + firstCharacter(words[words.length - 1])).toUpperCase(Locale.ROOT);
  }

  private static String firstCharacter(String value) {
    return new String(Character.toChars(value.codePointAt(0)));
  }

  private void tone(String name) {
    getStyleClass().removeIf(style -> style.startsWith("player-avatar-tone-"));
    face.getStyleClass().removeIf(style -> style.startsWith("player-avatar-tone-"));
    String tone = "player-avatar-tone-"
        + Math.floorMod(name.toLowerCase(Locale.ROOT).hashCode(), TONE_COUNT);
    getStyleClass().add(tone);
    face.getStyleClass().add(tone);
  }

  private void installTooltip(String text) {
    Tooltip.install(this, new Tooltip(text));
  }
}
