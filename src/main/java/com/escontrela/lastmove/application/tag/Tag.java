package com.escontrela.lastmove.application.tag;

import java.util.Locale;
import java.util.Objects;

/** A reusable user-defined label. Names are unique independently of their casing. */
public record Tag(long id, String name) {

  public Tag {
    if (id < 0) {
      throw new IllegalArgumentException("tag id must not be negative");
    }
    name = Objects.requireNonNull(name, "tag name must not be null").trim();
    if (name.isEmpty()) {
      throw new IllegalArgumentException("tag name must not be blank");
    }
    if (name.length() > 48) {
      throw new IllegalArgumentException("tag name must contain at most 48 characters");
    }
  }

  /** Canonical lookup key; the first created spelling remains the displayed label. */
  public static String normalizedName(String value) {
    String normalized = Objects.requireNonNull(value, "tag name must not be null").trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("tag name must not be blank");
    }
    if (normalized.length() > 48) {
      throw new IllegalArgumentException("tag name must contain at most 48 characters");
    }
    return normalized.toLowerCase(Locale.ROOT);
  }
}
