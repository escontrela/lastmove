package com.escontrela.lastmove.ui.service;

/** Short UI sound effects available to chess screens and reusable controls. */
public enum ChessSound {
  MOVE_SELF("/chess-sounds/move-self.mp3"),
  CAPTURE("/chess-sounds/capture.mp3"),
  PROMOTE("/chess-sounds/promote.mp3"),
  CASTLE("/chess-sounds/castle.mp3"),
  MOVE_CHECK("/chess-sounds/move-check.mp3"),
  NOTIFY("/chess-sounds/notify.mp3"),
  MEMORY_GAME_OVER("/sounds/mixkit-sad-game-over-trombone-471.wav"),
  MEMORY_GAME_COMPLETED("/sounds/mixkit-game-level-completed-2059.wav"),
  MEMORY_CLOCK_URGENT("/sounds/mixkit-clock-countdown-bleeps-916.wav"),
  MEMORY_INCORRECT_PIECE("/sounds/mixkit-small-hit-in-a-game-2072.wav"),
  MEMORY_CORRECT_PIECE("/sounds/mixkit-winning-a-coin-video-game-2069.wav"),
  MEMORY_PIECES_DISAPPEAR("/sounds/mixkit-air-zoom-vacuum-2608.wav"),
  MEMORY_NEW_POSITION("/sounds/mixkit-unlock-new-item-game-notification-254.wav"),
  MEMORY_BACKGROUND("/sounds/mixkit-little-birds-singing-in-the-trees-17.wav"),
  MEMORY_BACKGROUND_WIND("/sounds/mixkit-wind-blowing-ambience-2658.wav"),
  MEMORY_BACKGROUND_RAIN("/sounds/mixkit-light-rain-loop-2393.wav");

  private final String resourcePath;

  ChessSound(String resourcePath) {
    this.resourcePath = resourcePath;
  }

  String resourcePath() {
    return resourcePath;
  }
}
