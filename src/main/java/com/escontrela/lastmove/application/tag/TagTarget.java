package com.escontrela.lastmove.application.tag;

import java.util.Objects;

/** Identifies a labelable resource without coupling tags to one aggregate type. */
public record TagTarget(TagTargetType type, String id) {
  public TagTarget {
    type = Objects.requireNonNull(type, "target type must not be null");
    id = Objects.requireNonNull(id, "target id must not be null").trim();
    if (id.isEmpty()) {
      throw new IllegalArgumentException("target id must not be blank");
    }
  }
}
