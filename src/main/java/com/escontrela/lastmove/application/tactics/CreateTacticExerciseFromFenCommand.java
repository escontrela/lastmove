package com.escontrela.lastmove.application.tactics;

import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.tactics.TacticSuiteId;
import java.util.Objects;

/** Input for adding a solution-less exercise that can be authored in the tactics workspace. */
public record CreateTacticExerciseFromFenCommand(
    PlayerId ownerId, TacticSuiteId suiteId, String title, Fen fen) {
  public CreateTacticExerciseFromFenCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(suiteId, "suiteId must not be null");
    Objects.requireNonNull(title, "title must not be null");
    Objects.requireNonNull(fen, "fen must not be null");
  }
}
