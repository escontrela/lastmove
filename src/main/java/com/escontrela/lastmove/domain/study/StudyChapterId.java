package com.escontrela.lastmove.domain.study;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of one ordered chapter inside a study. */
public record StudyChapterId(UUID value) {

  public StudyChapterId {
    Objects.requireNonNull(value, "value must not be null");
  }

  /** Creates a new random chapter identity. */
  public static StudyChapterId random() {
    return new StudyChapterId(UUID.randomUUID());
  }
}
