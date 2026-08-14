package com.escontrela.lastmove.ui.component.session;

import java.util.Objects;

/** One UI-neutral session item rendered by {@link SessionSelectorControl}. */
public record SessionSelectorEntry(int sessionIndex, String title) {

  public SessionSelectorEntry {
    if (sessionIndex < 0) {
      throw new IllegalArgumentException("sessionIndex must not be negative");
    }
    title = Objects.requireNonNull(title, "title must not be null").trim();
    if (title.isEmpty()) {
      throw new IllegalArgumentException("title must not be blank");
    }
  }
}
