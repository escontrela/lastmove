package com.escontrela.lastmove.domain.analysis;

import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.util.Objects;
import java.util.Optional;

/**
 * Chess content shared by analysis sessions and persisted study chapters.
 *
 * <p>Owns the initial position, the declared source result and the authoritative move tree. The
 * user's reading state lives separately in {@link ChapterNavigation}.
 */
public final class AnalysisContent {

  private final PositionSnapshot initialPosition;
  private final Optional<GameResult> sourceResult;
  private final AnalysisTree tree;

  public AnalysisContent(
      PositionSnapshot initialPosition, Optional<GameResult> sourceResult, AnalysisTree tree) {
    this.initialPosition =
        Objects.requireNonNull(initialPosition, "initialPosition must not be null");
    this.sourceResult = Objects.requireNonNull(sourceResult, "sourceResult must not be null");
    this.tree = Objects.requireNonNull(tree, "tree must not be null");
  }

  public PositionSnapshot initialPosition() {
    return initialPosition;
  }

  public Optional<GameResult> sourceResult() {
    return sourceResult;
  }

  public AnalysisTree tree() {
    return tree;
  }
}
