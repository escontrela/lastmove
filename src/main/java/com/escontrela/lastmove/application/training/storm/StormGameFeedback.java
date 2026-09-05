package com.escontrela.lastmove.application.training.storm;

import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.application.tactics.TacticWorkspace;
import java.util.Objects;
import java.util.Optional;

/** Feedback for a Storm move or hint request. */
public record StormGameFeedback(
    boolean correct, boolean failed, boolean hintUsed, Optional<Square> hintSquare,
    Optional<Square> hintTargetSquare, boolean solved,
    Optional<TacticWorkspace> workspace) {
  public StormGameFeedback(boolean correct, boolean failed, boolean hintUsed,
      Optional<Square> hintSquare, boolean solved) {
    this(correct, failed, hintUsed, hintSquare, Optional.empty(), solved, Optional.empty());
  }
  public StormGameFeedback {
    hintSquare = Objects.requireNonNull(hintSquare, "hintSquare must not be null");
    hintTargetSquare = Objects.requireNonNull(hintTargetSquare, "hintTargetSquare must not be null");
    workspace = Objects.requireNonNull(workspace, "workspace must not be null");
    if ((hintSquare.isPresent() || hintTargetSquare.isPresent()) && !hintUsed)
      throw new IllegalArgumentException("hint squares require hintUsed");
  }
}
