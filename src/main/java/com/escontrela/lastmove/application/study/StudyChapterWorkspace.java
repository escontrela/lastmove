package com.escontrela.lastmove.application.study;

import com.escontrela.lastmove.application.dto.AnalysisNotationTree;
import com.escontrela.lastmove.domain.analysis.AnalysisOrigin;
import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.study.StudyChapterId;
import com.escontrela.lastmove.domain.study.StudyId;
import java.util.Objects;
import java.util.Optional;

/** Renderable state of one chapter's workspace: board positions and full notation tree. */
public record StudyChapterWorkspace(
    StudyId studyId,
    StudyChapterId chapterId,
    String title,
    AnalysisOrigin origin,
    PositionSnapshot initialPosition,
    PositionSnapshot currentPosition,
    Optional<GameResult> sourceResult,
    AnalysisNotationTree notationTree) {

  public StudyChapterWorkspace {
    Objects.requireNonNull(studyId, "studyId must not be null");
    Objects.requireNonNull(chapterId, "chapterId must not be null");
    title = Objects.requireNonNull(title, "title must not be null");
    Objects.requireNonNull(origin, "origin must not be null");
    Objects.requireNonNull(initialPosition, "initialPosition must not be null");
    Objects.requireNonNull(currentPosition, "currentPosition must not be null");
    sourceResult = Objects.requireNonNull(sourceResult, "sourceResult must not be null");
    Objects.requireNonNull(notationTree, "notationTree must not be null");
  }
}