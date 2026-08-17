package com.escontrela.lastmove.application.study;

import com.escontrela.lastmove.domain.game.ImportedPgnGame;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.study.StudyId;
import java.util.Objects;

/** Adds a chapter to a study whose move tree comes from an imported PGN game. */
public record ImportPgnChapterCommand(
    PlayerId ownerId, StudyId studyId, ImportedPgnGame importedGame) {

  public ImportPgnChapterCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(studyId, "studyId must not be null");
    Objects.requireNonNull(importedGame, "importedGame must not be null");
  }
}