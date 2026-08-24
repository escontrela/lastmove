package com.escontrela.lastmove.application.study;

import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.study.StudyChapterId;
import com.escontrela.lastmove.domain.study.StudyId;
import java.util.Objects;

/** Immutable context for editing one persisted chapter's initial position. */
public record StudyChapterPositionEditContext(
    PlayerId ownerId,
    StudyId studyId,
    StudyChapterId chapterId,
    PositionSnapshot initialPosition,
    boolean hasMoves) {

  public StudyChapterPositionEditContext {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(studyId, "studyId must not be null");
    Objects.requireNonNull(chapterId, "chapterId must not be null");
    Objects.requireNonNull(initialPosition, "initialPosition must not be null");
  }
}
