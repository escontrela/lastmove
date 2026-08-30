package com.escontrela.lastmove.application.arena;

import java.util.Objects;
import java.util.Optional;

/** Safe account information returned after validating a configured Lichess bot token. */
public record LichessBotAccount(String id, String username, Optional<Integer> standardRating,
    Optional<Integer> previousStandardRating) {
  public LichessBotAccount(String id, String username) {
    this(id, username, Optional.empty(), Optional.empty());
  }
  public LichessBotAccount {
    id = required(id, "id");
    username = required(username, "username");
    standardRating = standardRating == null ? Optional.empty() : standardRating;
    previousStandardRating = previousStandardRating == null ? Optional.empty() : previousStandardRating;
    if (standardRating.isPresent() && standardRating.get() < 0) throw new IllegalArgumentException("standardRating must not be negative");
    if (previousStandardRating.isPresent() && previousStandardRating.get() < 0) throw new IllegalArgumentException("previousStandardRating must not be negative");
  }

  private static String required(String value, String name) {
    String required = Objects.requireNonNull(value, name + " must not be null").trim();
    if (required.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
    return required;
  }
}
