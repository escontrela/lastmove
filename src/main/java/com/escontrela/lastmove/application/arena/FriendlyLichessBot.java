package com.escontrela.lastmove.application.arena;

import java.time.Instant;
import java.util.Optional;

/** A bot that has previously accepted one of this account's challenges. */
public record FriendlyLichessBot(String lichessId, String username, Optional<Integer> rating,
    Instant firstAcceptedAt, Instant lastAcceptedAt) {
  public FriendlyLichessBot {
    if (lichessId == null || lichessId.isBlank()) {
      throw new IllegalArgumentException("Lichess bot id must not be blank.");
    }
    if (username == null || username.isBlank()) username = lichessId;
    rating = rating == null ? Optional.empty() : rating;
    firstAcceptedAt = firstAcceptedAt == null ? Instant.now() : firstAcceptedAt;
    lastAcceptedAt = lastAcceptedAt == null ? firstAcceptedAt : lastAcceptedAt;
  }
}
