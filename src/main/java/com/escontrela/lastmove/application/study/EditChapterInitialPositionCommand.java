package com.escontrela.lastmove.application.study;

import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.study.StudyChapterId;
import com.escontrela.lastmove.domain.study.StudyId;
import java.util.Objects;

/** Requests replacement of a chapter's initial position. */
public record EditChapterInitialPositionCommand(
    PlayerId ownerId,
    StudyId studyId,
    StudyChapterId chapterId,
    PositionSnapshot initialPosition,
    boolean discardExistingMoves) {

  public EditChapterInitialPositionCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(studyId, "studyId must not be null");
    Objects.requireNonNull(chapterId, "chapterId must not be null");
    Objects.requireNonNull(initialPosition, "initialPosition must not be null");
  }
}
