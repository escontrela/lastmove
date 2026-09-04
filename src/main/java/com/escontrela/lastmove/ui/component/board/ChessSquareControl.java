package com.escontrela.lastmove.ui.component.board;

import javafx.geometry.Insets;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.AccessibleRole;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.css.PseudoClass;
import javafx.util.Duration;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.ui.support.CssClassNames;

/**
 * A single square on the {@link ChessBoardControl}.
 *
 * <p>Owns the background color, highlight state, and the piece image (if any).
 */
public class ChessSquareControl extends StackPane {
  private static final PseudoClass ANSWER_CORRECT = PseudoClass.getPseudoClass("answer-correct");
  private static final PseudoClass ANSWER_INCORRECT = PseudoClass.getPseudoClass("answer-incorrect");

  /** Proporción del tamaño de la casilla que ocupa la imagen de la pieza. */
  private double pieceScale = 0.88;

  private static final Border DRAG_TARGET_BORDER =
      new Border(
          new BorderStroke(
              Color.rgb(28, 105, 212, 0.75),
              BorderStrokeStyle.SOLID,
              CornerRadii.EMPTY,
              new BorderWidths(2),
              Insets.EMPTY));

  private final int file;
  private final int rank;
  private final boolean isLight;
  private BoardTheme theme;
  private boolean visualEffectsEnabled = true;
  private final ImageView pieceImageView =
      new ImageView(); // Cambiado el nombre de la variable para evitar colisión con el tipo Image
  private final Region threatenedOverlay = new Region();
  private Timeline answerFeedbackBlink;
  private Boolean answerFeedback;

  public ChessSquareControl(int file, int rank, boolean isLight, BoardTheme theme) {
    this.file = file;
    this.rank = rank;
    this.isLight = isLight;
    getStyleClass().add("chess-square");
    getStyleClass().add(isLight ? "chess-square-light" : "chess-square-dark");
    setAccessibleText("Square " + Square.of(file, rank).toAlgebraic());
    setAccessibleRole(AccessibleRole.BUTTON);
    setFocusTraversable(true);
    applyTheme(theme);
    setSnapToPixel(true);

    // La ImageView NO debe consumir eventos de mouse -> permitir que pasen a través
    pieceImageView.setPreserveRatio(true);
    pieceImageView.setPickOnBounds(false); // Clave: no interceptar eventos de mouse
    pieceImageView.getStyleClass().add("chess-piece");

    threatenedOverlay.setMouseTransparent(true);
    threatenedOverlay.getStyleClass().add("chess-square-threatened-glow");
    threatenedOverlay.setVisible(false);

    getChildren().add(pieceImageView);
    getChildren().add(threatenedOverlay);
  }

  private void applyTheme(BoardTheme theme) {
    this.theme = theme;
    String color = isLight ? theme.getLightColor() : theme.getDarkColor();
    String background = visualEffectsEnabled
        ? "linear-gradient(from 0% 0% to 100% 100%, derive(" + color + ", 8%) 0%, "
            + color + " 52%, derive(" + color + ", -12%) 100%)"
        : color;
    setStyle("-fx-background-color: " + background + ";");
  }

  /** Toggles the presentation-only gradient while keeping the active board palette intact. */
  public void setVisualEffectsEnabled(boolean enabled) {
    visualEffectsEnabled = enabled;
    applyTheme(theme);
  }

  /** Updates the palette in place so changing board presets preserves all square interaction state. */
  public void setTheme(BoardTheme theme) {
    applyTheme(theme);
  }

  public void setPieceScale(double scale) {
    pieceScale = scale;
  }

  public int getFile() {
    return file;
  }

  public int getRank() {
    return rank;
  }

  /** Activa o desactiva el borde sutil usado como destino potencial durante drag-drop. */
  public void setDragTarget(boolean dragTarget) {
    setBorder(dragTarget ? DRAG_TARGET_BORDER : null);
  }

  /** Marks this square as the source of a requested tactic hint. */
  public void setHint(boolean hint) {
    if (hint) {
      if (!getStyleClass().contains(CssClassNames.SQUARE_HINT)) {
        getStyleClass().add(CssClassNames.SQUARE_HINT);
      }
    } else {
      getStyleClass().remove(CssClassNames.SQUARE_HINT);
    }
  }
  public void setThreatened(boolean threatened) {
    threatenedOverlay.setVisible(threatened);
  }

  /**
   * Aplica el tamaño exacto de la casilla (en píxeles), calculado por {@link ChessBoardSkin} a
   * partir del espacio disponible en pantalla. Fijamos min/pref/max al mismo valor para que JavaFX
   * no decida por su cuenta un tamaño distinto, y calculamos nosotros mismos el tamaño de la pieza
   * en lugar de dejar que un binding reactivo la redimensione en cada pulso de layout.
   */
  public void setSquareSize(double size) {
    setMinSize(size, size);
    setPrefSize(size, size);
    setMaxSize(size, size);

    double pieceSize = Math.floor(size * pieceScale);
    pieceImageView.setFitWidth(pieceSize);
    pieceImageView.setFitHeight(pieceSize);
  }

  /** Devuelve la instancia del objeto Image actual (puede ser null si la casilla está vacía). */
  public Image getPieceImage() {
    return pieceImageView.getImage();
  }

  /** Permite asignar una imagen directamente pasándole el objeto Image de JavaFX. */
  public void setPieceImage(Image image) {
    pieceImageView.setImage(image);
  }

  /** Permite asignar directamente un objeto Image ya instanciado (o null para vaciar). */
  public void setPieceImageObject(Image image) {
    pieceImageView.setImage(image);
  }

  /** Updates the accessible description while retaining the square identity for screen readers. */
  public void setPieceAccessibility(PieceType type, PieceColor color) {
    setAccessibleText(color.name() + " " + type.name() + " on " + Square.of(file, rank).toAlgebraic());
  }

  public void clearPieceAccessibility() {
    setAccessibleText("Empty square " + Square.of(file, rank).toAlgebraic());
  }

  public void setAnswerFeedback(Boolean correct) {
    if (java.util.Objects.equals(answerFeedback, correct)) return;
    answerFeedback = correct;
    if (answerFeedbackBlink != null) {
      answerFeedbackBlink.stop();
      answerFeedbackBlink = null;
    }
    pieceImageView.setOpacity(1.0);
    pseudoClassStateChanged(ANSWER_CORRECT, Boolean.TRUE.equals(correct));
    pseudoClassStateChanged(ANSWER_INCORRECT, Boolean.FALSE.equals(correct));
    setAccessibleHelp(correct == null ? null : correct ? "Correct answer" : "Incorrect answer; correct piece shown");
    if (Boolean.FALSE.equals(correct)) {
      answerFeedbackBlink = new Timeline(
          new KeyFrame(Duration.ZERO, new KeyValue(pieceImageView.opacityProperty(), 1.0)),
          new KeyFrame(Duration.millis(250), new KeyValue(pieceImageView.opacityProperty(), 0.15)),
          new KeyFrame(Duration.millis(500), new KeyValue(pieceImageView.opacityProperty(), 1.0)));
      answerFeedbackBlink.setCycleCount(4);
      answerFeedbackBlink.play();
    }
  }

  /** Sets a presentation-only piece image; this control does not model chess rules. */
  public void setPieceImage(String resourcePath) {
    if (resourcePath == null) {
      pieceImageView.setImage(null);
      return;
    }
    var is = ChessSquareControl.class.getResourceAsStream(resourcePath);
    if (is == null) {
      throw new IllegalArgumentException("No se encontró el recurso: " + resourcePath);
    }
    Image image = new Image(is);
    pieceImageView.setImage(image);
  }
}
