package com.escontrela.lastmove.ui.component.game;

import com.escontrela.lastmove.domain.game.PositionPiece;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;

/** Compact presentation of captured pieces, overlapping equal pieces in countable groups. */
public final class CapturedPiecesControl extends FlowPane {

  private static final double PIECE_SIZE = 24.0;
  private static final double OVERLAP_OFFSET = 8.0;
  private static final List<PieceType> DISPLAY_ORDER =
      List.of(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN);

  public CapturedPiecesControl() {
    getStyleClass().add("captured-pieces");
    setAlignment(Pos.CENTER_LEFT);
    setHgap(4.0);
    setVgap(2.0);
    setPrefWrapLength(250.0);
    setAccessibleRole(AccessibleRole.PARENT);
  }

  /** Rebuilds the visuals from the supplied authoritative game-history projection. */
  public void render(List<PositionPiece> pieces) {
    Objects.requireNonNull(pieces, "pieces must not be null");
    List<CapturedPieceGroup> groups = groups(pieces);
    getChildren().setAll(groups.stream().map(this::groupView).toList());
    setAccessibleText(
        pieces.isEmpty()
            ? "No captured pieces"
            : groups.stream()
                .map(CapturedPieceGroup::accessibleText)
                .reduce((left, right) -> left + ", " + right)
                .orElse(""));
  }

  static List<CapturedPieceGroup> groups(List<PositionPiece> pieces) {
    Map<GroupKey, Integer> counts = new LinkedHashMap<>();
    pieces.stream()
        .sorted(Comparator.comparingInt(piece -> DISPLAY_ORDER.indexOf(piece.type())))
        .forEach(piece -> counts.merge(new GroupKey(piece.color(), piece.type()), 1, Integer::sum));
    List<CapturedPieceGroup> result = new ArrayList<>(counts.size());
    counts.forEach((key, count) -> result.add(new CapturedPieceGroup(key.color(), key.type(), count)));
    return List.copyOf(result);
  }

  private Pane groupView(CapturedPieceGroup group) {
    Pane pane = new Pane();
    pane.getStyleClass().add("captured-piece-group");
    pane.setMinSize(group.width(), PIECE_SIZE);
    pane.setPrefSize(group.width(), PIECE_SIZE);
    pane.setMaxSize(group.width(), PIECE_SIZE);
    pane.setAccessibleRole(AccessibleRole.PARENT);
    pane.setAccessibleText(group.accessibleText());
    for (int index = 0; index < group.count(); index++) {
      ImageView view = pieceView(group.color(), group.type());
      view.relocate(index * OVERLAP_OFFSET, 0);
      pane.getChildren().add(view);
    }
    return pane;
  }

  private ImageView pieceView(PieceColor color, PieceType type) {
    String key =
        color.name().toLowerCase(Locale.ROOT)
            + "-"
            + type.name().toLowerCase(Locale.ROOT);
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

  private record GroupKey(PieceColor color, PieceType type) {}

  record CapturedPieceGroup(PieceColor color, PieceType type, int count) {
    CapturedPieceGroup {
      Objects.requireNonNull(color);
      Objects.requireNonNull(type);
      if (count < 1) {
        throw new IllegalArgumentException("count must be positive");
      }
    }

    double width() {
      return PIECE_SIZE + ((count - 1) * OVERLAP_OFFSET);
    }

    String accessibleText() {
      String name = type.name().toLowerCase(Locale.ROOT);
      return count
          + " "
          + color.name().toLowerCase(Locale.ROOT)
          + " "
          + name
          + (count == 1 ? "" : "s");
    }
  }
}
