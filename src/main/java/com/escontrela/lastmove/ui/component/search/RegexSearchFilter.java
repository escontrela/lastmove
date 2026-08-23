package com.escontrela.lastmove.ui.component.search;

import java.util.Objects;
import java.util.regex.Pattern;

/** Pure presentation helper for matching the explicitly selected fields of a library row. */
public final class RegexSearchFilter {

  private RegexSearchFilter() {}

  public static boolean matches(Pattern pattern, String... searchableFields) {
    Objects.requireNonNull(pattern, "pattern must not be null");
    for (String field : searchableFields) {
      if (field != null && pattern.matcher(field).find()) {
        return true;
      }
    }
    return false;
  }
}
