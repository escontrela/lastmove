package com.escontrela.lastmove.ui.component.board;

/** Visual piece collection independently selectable from the board finish. */
public enum ChessPieceSet {
  STD("STD", "/chess-pieces", 0.88),
  LASTMOVE("LastMove", "/chess-pieces-lastmove", 0.88);

  private final String displayName;
  private final String resourceRoot;
  private final double scale;

  ChessPieceSet(String displayName, String resourceRoot, double scale) {
    this.displayName = displayName;
    this.resourceRoot = resourceRoot;
    this.scale = scale;
  }

  public String displayName() { return displayName; }

  String resourceRoot() { return resourceRoot; }

  double scale() { return scale; }

  @Override
  public String toString() { return displayName; }
}
