package com.escontrela.lastmove.ui.service;

/** Short UI sound effects available to chess screens and reusable controls. */
public enum ChessSound {
  MOVE_SELF("/chess-sounds/move-self.mp3"),
  CAPTURE("/chess-sounds/capture.mp3"),
  PROMOTE("/chess-sounds/promote.mp3"),
  CASTLE("/chess-sounds/castle.mp3"),
  MOVE_CHECK("/chess-sounds/move-check.mp3"),
  NOTIFY("/chess-sounds/notify.mp3");

  private final String resourcePath;

  ChessSound(String resourcePath) {
    this.resourcePath = resourcePath;
  }

  String resourcePath() {
    return resourcePath;
  }
}
