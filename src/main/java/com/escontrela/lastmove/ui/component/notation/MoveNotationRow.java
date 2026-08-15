package com.escontrela.lastmove.ui.component.notation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One rendered full-move row, including its indentation within the variation tree. */
public record MoveNotationRow(
    int moveNumber,
    Optional<MoveNotationEntry> whiteMove,
    Optional<MoveNotationEntry> blackMove,
    int depth,
    boolean variationStart) {

  public MoveNotationRow {
    if (moveNumber < 1) {
      throw new IllegalArgumentException("moveNumber must be at least one");
    }
    whiteMove = Objects.requireNonNull(whiteMove, "whiteMove must not be null");
    blackMove = Objects.requireNonNull(blackMove, "blackMove must not be null");
    if (depth < 0) {
      throw new IllegalArgumentException("depth must not be negative");
    }
    whiteMove.ifPresent(move -> validateEntry(move, moveNumber, true));
    blackMove.ifPresent(move -> validateEntry(move, moveNumber, false));
    if (whiteMove.isEmpty() && blackMove.isEmpty()) {
      throw new IllegalArgumentException("a notation row requires at least one move");
    }
  }

  /** Groups a single sequential line into full-move rows. */
  public static List<MoveNotationRow> group(List<MoveNotationEntry> entries) {
    return groupLine(entries, 0, false);
  }

  /** Flattens a move tree into selectable main-line rows followed by indented sibling branches. */
  public static List<MoveNotationRow> flatten(List<MoveNotationNode> roots) {
    List<MoveNotationNode> required =
        List.copyOf(Objects.requireNonNull(roots, "roots must not be null"));
    List<MoveNotationRow> rows = new ArrayList<>();
    appendSiblings(required, 0, rows);
    return List.copyOf(rows);
  }

  private static void appendSiblings(
      List<MoveNotationNode> siblings, int depth, List<MoveNotationRow> rows) {
    if (siblings.isEmpty()) {
      return;
    }
    MoveNotationNode primary = Objects.requireNonNull(siblings.getFirst());
    appendBranch(primary, depth, depth > 0, rows);
    for (int index = 1; index < siblings.size(); index++) {
      appendBranch(Objects.requireNonNull(siblings.get(index)), depth + 1, true, rows);
    }
  }

  private static void appendBranch(
      MoveNotationNode branchRoot,
      int depth,
      boolean variationStart,
      List<MoveNotationRow> rows) {
    List<MoveNotationEntry> uninterruptedLine = new ArrayList<>();
    MoveNotationNode current = branchRoot;
    boolean includeCurrent = true;
    while (true) {
      if (includeCurrent) {
        uninterruptedLine.add(current.entry());
      }
      includeCurrent = true;
      List<MoveNotationNode> continuations = current.continuations();
      if (continuations.size() > 1) {
        MoveNotationNode primary = continuations.getFirst();
        uninterruptedLine.add(primary.entry());
        rows.addAll(groupLine(uninterruptedLine, depth, variationStart));
        uninterruptedLine.clear();
        for (int index = 1; index < continuations.size(); index++) {
          appendBranch(continuations.get(index), depth + 1, true, rows);
        }
        current = primary;
        includeCurrent = false;
        variationStart = false;
      } else if (continuations.size() == 1) {
        current = continuations.getFirst();
      } else {
        rows.addAll(groupLine(uninterruptedLine, depth, variationStart));
        return;
      }
    }
  }

  private static List<MoveNotationRow> groupLine(
      List<MoveNotationEntry> entries, int depth, boolean variationStart) {
    List<MoveNotationRow> rows = new ArrayList<>();
    MoveNotationRow pending = null;
    boolean firstRow = true;
    for (MoveNotationEntry entry : entries) {
      MoveNotationEntry move = Objects.requireNonNull(entry, "entries must not contain null");
      if (pending != null
          && pending.moveNumber() == move.moveNumber()
          && pending.depth() == depth
          && pending.whiteMove().isPresent() != move.whiteMove()) {
        pending =
            new MoveNotationRow(
                move.moveNumber(),
                move.whiteMove() ? Optional.of(move) : pending.whiteMove(),
                move.whiteMove() ? pending.blackMove() : Optional.of(move),
                depth,
                pending.variationStart());
        rows.set(rows.size() - 1, pending);
      } else {
        pending =
            new MoveNotationRow(
                move.moveNumber(),
                move.whiteMove() ? Optional.of(move) : Optional.empty(),
                move.whiteMove() ? Optional.empty() : Optional.of(move),
                depth,
                firstRow && variationStart);
        rows.add(pending);
        firstRow = false;
      }
    }
    return rows;
  }

  private static void validateEntry(
      MoveNotationEntry entry, int expectedMoveNumber, boolean expectedWhite) {
    if (entry.moveNumber() != expectedMoveNumber || entry.whiteMove() != expectedWhite) {
      throw new IllegalArgumentException("notation entry does not match its row");
    }
  }
}
