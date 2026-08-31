package com.escontrela.lastmove.application.arena;

import java.util.Objects;
import java.util.Optional;

/** Safe account information returned after validating a configured Lichess bot token. */
public record LichessBotAccount(String id, String username, Optional<Integer> blitzRating,
    Optional<Integer> rapidRating, Optional<Integer> standardRating,
    Optional<Integer> previousStandardRating) {
  public LichessBotAccount(String id, String username) {
    this(id, username, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }
  /** Compatibility constructor for settings stored before the per-performance ratings existed. */
  public LichessBotAccount(String id, String username, Optional<Integer> standardRating,
      Optional<Integer> previousStandardRating) {
    this(id, username, Optional.empty(), Optional.empty(), standardRating, previousStandardRating);
  }
  public LichessBotAccount {
    id = required(id, "id");
    username = required(username, "username");
    blitzRating = valid(blitzRating, "blitzRating");
    rapidRating = valid(rapidRating, "rapidRating");
    standardRating = valid(standardRating, "standardRating");
    previousStandardRating = previousStandardRating == null ? Optional.empty() : previousStandardRating;
    if (previousStandardRating.isPresent() && previousStandardRating.get() < 0) throw new IllegalArgumentException("previousStandardRating must not be negative");
  }

  private static Optional<Integer> valid(Optional<Integer> rating, String name) {
    Optional<Integer> value = rating == null ? Optional.empty() : rating;
    if (value.isPresent() && value.get() < 0) throw new IllegalArgumentException(name + " must not be negative");
    return value;
  }

  private static String required(String value, String name) {
    String required = Objects.requireNonNull(value, name + " must not be null").trim();
    if (required.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
    return required;
  }
}
