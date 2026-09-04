package com.escontrela.lastmove.application.tactics;

import com.escontrela.lastmove.domain.game.ImportedPgnGame;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.tactics.TacticSuiteId;
import java.util.Objects;

/** Imports one parsed PGN as a tactic: its start position and move tree become the solution. */
public record ImportPgnTacticExerciseCommand(
    PlayerId ownerId, TacticSuiteId suiteId, ImportedPgnGame importedGame) {
  public ImportPgnTacticExerciseCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(suiteId, "suiteId must not be null");
    Objects.requireNonNull(importedGame, "importedGame must not be null");
  }
}
