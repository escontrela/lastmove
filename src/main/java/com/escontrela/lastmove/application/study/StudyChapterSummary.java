package com.escontrela.lastmove.application.study;

import com.escontrela.lastmove.domain.study.StudyChapterId;
import com.escontrela.lastmove.domain.analysis.AnalysisOrigin;
import java.time.Instant;
import java.util.Objects;

/** Immutable chapter-list item for a study's chapter panel. */
public record StudyChapterSummary(
    StudyChapterId chapterId,
    String title,
    AnalysisOrigin origin,
    int moveCount,
    Instant createdAt,
    Instant updatedAt) {

  public StudyChapterSummary {
    Objects.requireNonNull(chapterId, "chapterId must not be null");
    title = Objects.requireNonNull(title, "title must not be null");
    Objects.requireNonNull(origin, "origin must not be null");
    if (moveCount < 0) {
      throw new IllegalArgumentException("moveCount must not be negative");
    }
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }
}