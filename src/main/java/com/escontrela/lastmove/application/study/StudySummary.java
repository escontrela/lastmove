package com.escontrela.lastmove.application.study;

import com.escontrela.lastmove.domain.study.StudyId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Immutable study-library list item that does not expose the mutable aggregate. */
public record StudySummary(
    StudyId studyId,
    String title,
    Optional<String> description,
    int chapterCount,
    Instant createdAt,
    Instant updatedAt) {

  public StudySummary {
    Objects.requireNonNull(studyId, "studyId must not be null");
    title = Objects.requireNonNull(title, "title must not be null");
    description = Objects.requireNonNull(description, "description must not be null");
    if (chapterCount < 0) {
      throw new IllegalArgumentException("chapterCount must not be negative");
    }
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }
}