package com.escontrela.lastmove.ui.component.search;

import java.util.Objects;
import java.util.regex.Pattern;
import javafx.event.Event;
import javafx.event.EventType;

/** Event emitted by {@link RegexSearchControl} after a valid regular expression is submitted. */
public final class RegexSearchEvent extends Event {

  public static final EventType<RegexSearchEvent> SEARCH =
      new EventType<>(Event.ANY, "REGEX_SEARCH");

  private final String query;
  private final Pattern pattern;

  RegexSearchEvent(Object source, String query, Pattern pattern) {
    super(source, null, SEARCH);
    this.query = Objects.requireNonNull(query, "query must not be null");
    this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
  }

  public String query() {
    return query;
  }

  public Pattern pattern() {
    return pattern;
  }
}
