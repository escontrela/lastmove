package com.escontrela.lastmove.ui.component.board;

/**
 * Complete presentation preset for a reusable chess board.
 *
 * <p>The preset belongs exclusively to the JavaFX presentation layer. It deliberately contains no
 * chess state or rules and can therefore be applied to every {@link ChessBoardControl} uniformly.
 */
public enum BoardAppearancePreset {
  STANDARD("Standard", BoardTheme.LASTMOVE, "/chess-pieces", 0.88, false),
  V2("v2", BoardTheme.V2, "/chess-pieces", 0.91, true);

  private final String displayName;
  private final BoardTheme boardTheme;
  private final String pieceResourceRoot;
  private final double pieceScale;
  private final boolean framed;

  BoardAppearancePreset(
      String displayName,
      BoardTheme boardTheme,
      String pieceResourceRoot,
      double pieceScale,
      boolean framed) {
    this.displayName = displayName;
    this.boardTheme = boardTheme;
    this.pieceResourceRoot = pieceResourceRoot;
    this.pieceScale = pieceScale;
    this.framed = framed;
  }

  public String displayName() {
    return displayName;
  }

  BoardTheme boardTheme() {
    return boardTheme;
  }

  String pieceResourceRoot() {
    return pieceResourceRoot;
  }

  double pieceScale() {
    return pieceScale;
  }

  boolean framed() {
    return framed;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
