package com.escontrela.lastmove.domain.game;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable export of a progressive game suitable for persistence or conversion into a study.
 *
 * <p>The record contains only domain data; the injected rules engine and mutable game aggregate are
 * deliberately excluded. Analysis conversion copies the recorded plies into a new independent tree.
 */
public record GameRecord(
    GameId sourceGameId,
    String title,
    PositionSnapshot initialPosition,
    Optional<GamePlayer> whitePlayer,
    Optional<GamePlayer> blackPlayer,
    Optional<TimeControl> timeControl,
    List<RecordedPly> moves,
    Optional<GameResult> result,
    Optional<GameTerminationReason> terminationReason) {

  public GameRecord {
    Objects.requireNonNull(sourceGameId, "sourceGameId must not be null");
    title = Objects.requireNonNull(title, "title must not be null");
    if (title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    Objects.requireNonNull(initialPosition, "initialPosition must not be null");
    whitePlayer = Objects.requireNonNull(whitePlayer, "whitePlayer must not be null");
    blackPlayer = Objects.requireNonNull(blackPlayer, "blackPlayer must not be null");
    timeControl = Objects.requireNonNull(timeControl, "timeControl must not be null");
    moves = List.copyOf(Objects.requireNonNull(moves, "moves must not be null"));
    result = Objects.requireNonNull(result, "result must not be null");
    terminationReason =
        Objects.requireNonNull(terminationReason, "terminationReason must not be null");
    if (result.isPresent() != terminationReason.isPresent()) {
      throw new IllegalArgumentException("a game result and its termination reason must coexist");
    }
    for (int index = 1; index < moves.size(); index++) {
      if (!moves.get(index - 1).clockAfterMove().equals(moves.get(index).clockBeforeMove())) {
        throw new IllegalArgumentException("Recorded clock snapshots must form one timeline");
      }
    }
  }

  /** Returns the final recorded position, or the initial position for a game without moves. */
  public PositionSnapshot currentPosition() {
    return moves.isEmpty() ? initialPosition : moves.getLast().ply().resultingPosition();
  }
}
