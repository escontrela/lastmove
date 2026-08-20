package com.escontrela.lastmove.ui.component.game;

import com.escontrela.lastmove.domain.game.PositionPiece;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;

/** Compact, responsive presentation of the pieces of one colour lost during a game. */
public final class CapturedPiecesControl extends FlowPane {

  private static final double PIECE_SIZE = 24.0;

  public CapturedPiecesControl() {
    getStyleClass().add("captured-pieces");
    setAlignment(Pos.CENTER_LEFT);
    setHgap(-4.0);
    setVgap(-3.0);
    setPrefWrapLength(250.0);
    setAccessibleRole(AccessibleRole.PARENT);
  }

  /** Rebuilds the visuals from the supplied authoritative game-history projection. */
  public void render(List<PositionPiece> pieces) {
    Objects.requireNonNull(pieces, "pieces must not be null");
    getChildren().setAll(pieces.stream().map(this::pieceView).toList());
    setAccessibleText(
        pieces.isEmpty()
            ? "No captured pieces"
            : pieces.size() + (pieces.size() == 1 ? " captured piece" : " captured pieces"));
  }

  private ImageView pieceView(PositionPiece piece) {
    String key =
        piece.color().name().toLowerCase(Locale.ROOT)
            + "-"
            + piece.type().name().toLowerCase(Locale.ROOT);
    String path = "/chess-pieces/" + key + ".png";
    ImageView view =
        new ImageView(
            new Image(
                Objects.requireNonNull(
                        getClass().getResource(path), () -> "Missing captured-piece resource " + path)
                    .toExternalForm()));
    view.setFitWidth(PIECE_SIZE);
    view.setFitHeight(PIECE_SIZE);
    view.setPreserveRatio(true);
    view.setSmooth(true);
    view.setMouseTransparent(true);
    view.setAccessibleText(key.replace('-', ' '));
    return view;
  }
}
