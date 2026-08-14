package com.escontrela.lastmove.ui.component.notation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** One full-move row containing independently selectable White and Black plies. */
public record MoveNotationRow(
    int moveNumber,
    Optional<MoveNotationEntry> whiteMove,
    Optional<MoveNotationEntry> blackMove) {

  public MoveNotationRow {
    if (moveNumber < 1) {
      throw new IllegalArgumentException("moveNumber must be at least one");
    }
    whiteMove = Objects.requireNonNull(whiteMove, "whiteMove must not be null");
    blackMove = Objects.requireNonNull(blackMove, "blackMove must not be null");
    whiteMove.ifPresent(move -> validateEntry(move, moveNumber, true));
    blackMove.ifPresent(move -> validateEntry(move, moveNumber, false));
    if (whiteMove.isEmpty() && blackMove.isEmpty()) {
      throw new IllegalArgumentException("a notation row requires at least one move");
    }
  }

  /** Groups a sequential line into Lichess-style full-move rows. */
  public static List<MoveNotationRow> group(List<MoveNotationEntry> entries) {
    List<MoveNotationEntry> required =
        List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
    Map<Integer, MoveNotationEntry[]> movesByNumber = new LinkedHashMap<>();
    for (MoveNotationEntry entry : required) {
      MoveNotationEntry move = Objects.requireNonNull(entry, "entries must not contain null");
      MoveNotationEntry[] pair =
          movesByNumber.computeIfAbsent(move.moveNumber(), ignored -> new MoveNotationEntry[2]);
      int colorIndex = move.whiteMove() ? 0 : 1;
      if (pair[colorIndex] != null) {
        throw new IllegalArgumentException(
            "the visible line contains two moves for the same color and move number");
      }
      pair[colorIndex] = move;
    }

    List<MoveNotationRow> rows = new ArrayList<>(movesByNumber.size());
    movesByNumber.forEach(
        (moveNumber, pair) ->
            rows.add(
                new MoveNotationRow(
                    moveNumber, Optional.ofNullable(pair[0]), Optional.ofNullable(pair[1]))));
    return List.copyOf(rows);
  }

  private static void validateEntry(
      MoveNotationEntry entry, int expectedMoveNumber, boolean expectedWhite) {
    if (entry.moveNumber() != expectedMoveNumber || entry.whiteMove() != expectedWhite) {
      throw new IllegalArgumentException("notation entry does not match its row");
    }
  }
}
