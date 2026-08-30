package com.escontrela.lastmove.application.arena;

import java.time.Instant;
import java.util.Optional;

/** Durable view of a bot-eligible Lichess Arena tournament and Knight Shade's registration. */
public record ArenaTournament(
    String lichessTournamentId,
    String name,
    ArenaTournamentStatus remoteStatus,
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
    Optional<String> url,
    ArenaTournamentRegistrationStatus registrationStatus,
    Optional<String> lastError,
    Instant lastSeenAt,
    Instant updatedAt) {

  public ArenaTournament {
    if (lichessTournamentId == null || lichessTournamentId.isBlank()) throw new IllegalArgumentException("Tournament id must not be blank.");
    if (name == null || name.isBlank()) throw new IllegalArgumentException("Tournament name must not be blank.");
    if (remoteStatus == null || registrationStatus == null) throw new IllegalArgumentException("Tournament states must not be null.");
    if (variant == null || variant.isBlank()) throw new IllegalArgumentException("Tournament variant must not be blank.");
    if (clockLimitSeconds < 0 || clockIncrementSeconds < 0 || durationMinutes < 0 || playerCount < 0) throw new IllegalArgumentException("Tournament counts must not be negative.");
    minimumRating = minimumRating == null ? Optional.empty() : minimumRating;
    maximumRating = maximumRating == null ? Optional.empty() : maximumRating;
    startsAt = startsAt == null ? Optional.empty() : startsAt;
    finishesAt = finishesAt == null ? Optional.empty() : finishesAt;
    secondsToStart = secondsToStart == null ? Optional.empty() : secondsToStart;
    url = url == null ? Optional.empty() : url;
    lastError = lastError == null ? Optional.empty() : lastError;
    if (lastSeenAt == null || updatedAt == null) throw new IllegalArgumentException("Tournament timestamps must not be null.");
  }

  public static ArenaTournament discovered(LichessTournamentSnapshot snapshot, Instant observedAt) {
    return from(snapshot, initialRegistrationStatus(snapshot), Optional.empty(), observedAt, observedAt);
  }

  public ArenaTournament reconcile(LichessTournamentSnapshot snapshot, Instant observedAt) {
    if (!lichessTournamentId.equals(snapshot.id())) throw new IllegalArgumentException("Cannot reconcile different tournaments.");
    ArenaTournamentRegistrationStatus next = remoteRegistrationStatus(snapshot);
    if (next == null) next = registrationStatus;
    Optional<String> error = next == ArenaTournamentRegistrationStatus.ERROR ? lastError : Optional.empty();
    return from(snapshot, next, error, observedAt, observedAt);
  }

  public ArenaTournament withRegistration(ArenaTournamentRegistrationStatus next, Optional<String> error, Instant changedAt) {
    if (!registrationStatus.canTransitionTo(next)) {
      throw new IllegalStateException("Invalid tournament registration transition: " + registrationStatus + " -> " + next);
    }
    return new ArenaTournament(lichessTournamentId, name, remoteStatus, variant, rated, clockLimitSeconds,
        clockIncrementSeconds, durationMinutes, playerCount, minimumRating, maximumRating, botsAllowed,
        startsAt, finishesAt, secondsToStart, url, next, error, lastSeenAt, changedAt);
  }

  private static ArenaTournament from(LichessTournamentSnapshot source, ArenaTournamentRegistrationStatus registration,
      Optional<String> error, Instant observedAt, Instant updatedAt) {
    return new ArenaTournament(source.id(), source.name(), source.status(), source.variant(), source.rated(),
        source.clockLimitSeconds(), source.clockIncrementSeconds(), source.durationMinutes(), source.playerCount(),
        source.minimumRating(), source.maximumRating(), source.botsAllowed(), source.startsAt(), source.finishesAt(),
        source.secondsToStart(), source.url(), registration, error, observedAt, updatedAt);
  }

  private static ArenaTournamentRegistrationStatus initialRegistrationStatus(LichessTournamentSnapshot source) {
    ArenaTournamentRegistrationStatus forced = remoteRegistrationStatus(source);
    return forced == null ? ArenaTournamentRegistrationStatus.AVAILABLE : forced;
  }

  private static ArenaTournamentRegistrationStatus remoteRegistrationStatus(LichessTournamentSnapshot source) {
    if (source.status().isClosed()) return ArenaTournamentRegistrationStatus.CLOSED;
    if (source.status() == ArenaTournamentStatus.UNKNOWN) return ArenaTournamentRegistrationStatus.NOT_ELIGIBLE;
    if (!source.botsAllowed()) return ArenaTournamentRegistrationStatus.NOT_ELIGIBLE;
    if (!"standard".equalsIgnoreCase(source.variant())) return ArenaTournamentRegistrationStatus.INCOMPATIBLE;
    return null;
  }
}
