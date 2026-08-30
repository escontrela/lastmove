package com.escontrela.lastmove.application.arena;

import java.time.Instant;
import java.util.Optional;

/** Transport-independent tournament data received from Lichess. */
public record LichessTournamentSnapshot(
    String id,
    String name,
    ArenaTournamentStatus status,
    String variant,
    boolean rated,
    int clockLimitSeconds,
    int clockIncrementSeconds,
    int durationMinutes,
    int playerCount,
    Optional<Integer> minimumRating,
    Optional<Integer> maximumRating,
    boolean botsAllowed,
    Optional<Instant> startsAt,
    Optional<Instant> finishesAt,
    Optional<Integer> secondsToStart,
    Optional<String> url) {

  public LichessTournamentSnapshot {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("Tournament id must not be blank.");
    if (name == null || name.isBlank()) throw new IllegalArgumentException("Tournament name must not be blank.");
    if (status == null) throw new IllegalArgumentException("Tournament status must not be null.");
    if (variant == null || variant.isBlank()) throw new IllegalArgumentException("Tournament variant must not be blank.");
    if (clockLimitSeconds < 0 || clockIncrementSeconds < 0 || durationMinutes < 0 || playerCount < 0) {
      throw new IllegalArgumentException("Tournament time and player counts must not be negative.");
    }
    minimumRating = minimumRating == null ? Optional.empty() : minimumRating;
    maximumRating = maximumRating == null ? Optional.empty() : maximumRating;
    startsAt = startsAt == null ? Optional.empty() : startsAt;
    finishesAt = finishesAt == null ? Optional.empty() : finishesAt;
    secondsToStart = secondsToStart == null ? Optional.empty() : secondsToStart;
    url = url == null ? Optional.empty() : url;
  }

  public boolean supportsKnightShade() {
    return botsAllowed && "standard".equalsIgnoreCase(variant) && !status.isClosed();
  }
}
