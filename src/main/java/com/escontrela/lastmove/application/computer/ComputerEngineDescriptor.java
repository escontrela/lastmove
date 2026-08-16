package com.escontrela.lastmove.application.computer;

import java.util.Objects;

/** Stable, display-friendly identity of a computer opponent implementation. */
public record ComputerEngineDescriptor(String id, String displayName, String version) {

  public ComputerEngineDescriptor {
    id = requireText(id, "id");
    displayName = requireText(displayName, "displayName");
    version = requireText(version, "version");
  }

  private static String requireText(String value, String field) {
    String required = Objects.requireNonNull(value, field + " must not be null").trim();
    if (required.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return required;
  }
}
