package com.escontrela.lastmove.application.arena;

import java.time.Instant;
import java.util.Optional;

/** A challengeable bot reported by Lichess' official online-bots endpoint. */
public record LichessBotCandidate(String id, String username, boolean online, boolean idle,
    Optional<Integer> rating, Optional<String> playingId, Instant observedAt) {
  public LichessBotCandidate {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("Bot id must not be blank.");
    if (username == null || username.isBlank()) throw new IllegalArgumentException("Bot username must not be blank.");
    rating = rating == null ? Optional.empty() : rating;
    playingId = playingId == null ? Optional.empty() : playingId;
    observedAt = observedAt == null ? Instant.now() : observedAt;
  }
  public boolean available() { return online && idle && playingId.isEmpty(); }
}
