package com.escontrela.lastmove.ui.component.board;

import javafx.geometry.Insets;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * A single square on the {@link ChessBoardControl}.
 *
 * <p>Owns the background color, highlight state, and the piece image (if any).
 */
public class ChessSquareControl extends StackPane {

  /** Proporción del tamaño de la casilla que ocupa la imagen de la pieza. */
  private static final double PIECE_SCALE = 0.88;

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
  private final ImageView pieceImageView =
      new ImageView(); // Cambiado el nombre de la variable para evitar colisión con el tipo Image

  public ChessSquareControl(int file, int rank, boolean isLight, BoardTheme theme) {
    this.file = file;
    this.rank = rank;
    this.isLight = isLight;
    getStyleClass().add("chess-square");
    getStyleClass().add(isLight ? "chess-square-light" : "chess-square-dark");
    applyTheme(theme);
    setSnapToPixel(true);

    // La ImageView NO debe consumir eventos de mouse -> permitir que pasen a través
    pieceImageView.setPreserveRatio(true);
    pieceImageView.setPickOnBounds(false); // Clave: no interceptar eventos de mouse

    getChildren().add(pieceImageView);
  }

  private void applyTheme(BoardTheme theme) {
    String color = isLight ? theme.getLightColor() : theme.getDarkColor();
    setStyle("-fx-background-color: " + color + ";");
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

    double pieceSize = Math.floor(size * PIECE_SCALE);
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
