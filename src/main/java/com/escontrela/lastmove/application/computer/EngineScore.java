package com.escontrela.lastmove.application.computer;

import java.util.Objects;

/**
 * Engine-reported evaluation of a position, expressed either in centipawns or as a mate distance.
 *
 * <p>The sign convention matches the UCI protocol and Knightshade: a positive value favours the
 * side to move. Centipawn values are integers; mate values carry the number of plies until mate
 * (positive when the side to move delivers mate, negative when it receives mate).
 */
public record EngineScore(int value, boolean mate) {

  /** Creates a centipawn evaluation from the side-to-move perspective. */
  public static EngineScore centipawns(int centipawns) {
    return new EngineScore(centipawns, false);
  }

  /** Creates a mate evaluation from the side-to-move perspective, {@code plies} until mate. */
  public static EngineScore mateIn(int plies) {
    return new EngineScore(Objects.requireNonNull(plies, "plies must not be null"), true);
  }

  /** Returns whether this evaluation is a mate distance rather than a centipawn value. */
  public boolean isMate() {
    return mate;
  }
}
