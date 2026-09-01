package com.escontrela.lastmove.ui.component.board;

/**
 * Complete presentation preset for a reusable chess board.
 *
 * <p>The preset belongs exclusively to the JavaFX presentation layer. It deliberately contains no
 * chess state or rules and can therefore be applied to every {@link ChessBoardControl} uniformly.
 */
public enum BoardAppearancePreset {
  STANDARD("Standard", BoardTheme.LASTMOVE, "/chess-pieces", 0.88, false, null),
  V2("v2 Wood", BoardTheme.V2, "/chess-pieces", 0.91, true, "board-v2-wood"),
  V2_GRAY("v2 Gray", BoardTheme.V2_GRAY, "/chess-pieces", 0.91, true, "board-v2-gray");

  private final String displayName;
  private final BoardTheme boardTheme;
  private final String pieceResourceRoot;
  private final double pieceScale;
  private final boolean framed;
  private final String styleClass;

  BoardAppearancePreset(
      String displayName,
      BoardTheme boardTheme,
      String pieceResourceRoot,
      double pieceScale,
      boolean framed,
      String styleClass) {
    this.displayName = displayName;
    this.boardTheme = boardTheme;
    this.pieceResourceRoot = pieceResourceRoot;
    this.pieceScale = pieceScale;
    this.framed = framed;
    this.styleClass = styleClass;
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

  String styleClass() {
    return styleClass;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
