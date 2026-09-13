package com.escontrela.lastmove.ui.component.board;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import java.net.URL;
import java.util.Locale;
import javafx.scene.image.Image;

/** Resolves artwork from the chess set selected in the application preferences. */
public final class ChessPieceImageResolver {
  private ChessPieceImageResolver() {}

  public static String resourcePath(ChessPieceSet pieceSet, PieceColor color, PieceType type) {
    return pieceSet.resourceRoot() + "/" + color.name().toLowerCase(Locale.ROOT) + "-"
        + type.name().toLowerCase(Locale.ROOT) + ".png";
  }

  public static Image image(ChessPieceSet pieceSet, PieceColor color, PieceType type) {
    String path = resourcePath(pieceSet, color, type);
    URL resource = ChessPieceImageResolver.class.getResource(path);
    if (resource == null) throw new IllegalStateException("Missing chess piece resource " + path);
    return new Image(resource.toExternalForm());
  }
}
