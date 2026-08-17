package com.escontrela.lastmove.application.study;

import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.study.StudyId;
import java.util.Objects;

/** Adds a chapter to a study from a FEN position. */
public record CreateChapterFromFenCommand(PlayerId ownerId, StudyId studyId, String title, Fen fen) {

  public CreateChapterFromFenCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(studyId, "studyId must not be null");
    title = Objects.requireNonNull(title, "title must not be null");
    Objects.requireNonNull(fen, "fen must not be null");
  }
}