package com.escontrela.lastmove.ui.model;

import com.escontrela.lastmove.application.arena.ArenaTournament;
import com.escontrela.lastmove.application.arena.ArenaTournamentRegistrationStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Presentation-only, JavaFX-free content for one compact Challenger tournament row. */
public record TournamentRowSummary(String title, String details, String registration, boolean canRequestRegistration) {
  public static TournamentRowSummary from(ArenaTournament tournament, Instant now) {
    String clock = formatClock(tournament.clockLimitSeconds(), tournament.clockIncrementSeconds());
    String ratings = ratingRange(tournament.minimumRating(), tournament.maximumRating());
    String timing = timing(tournament, now);
    String details = String.join(" · ", clock, tournament.rated() ? "Rated" : "Casual", ratings,
        tournament.playerCount() + " players", timing);
    return new TournamentRowSummary(tournament.name(), details, registration(tournament.registrationStatus()),
        tournament.registrationStatus() == ArenaTournamentRegistrationStatus.AVAILABLE);
  }

  private static String formatClock(int limitSeconds, int incrementSeconds) {
    return (limitSeconds / 60.0 == Math.rint(limitSeconds / 60.0)
        ? Integer.toString(limitSeconds / 60)
        : String.format(java.util.Locale.ROOT, "%.1f", limitSeconds / 60.0)) + "+" + incrementSeconds;
  }

  private static String ratingRange(Optional<Integer> minimum, Optional<Integer> maximum) {
    if (minimum.isPresent() && maximum.isPresent()) return minimum.get() + "–" + maximum.get();
    if (minimum.isPresent()) return minimum.get() + "+";
    if (maximum.isPresent()) return "≤" + maximum.get();
    return "Any rating";
  }

  private static String timing(ArenaTournament tournament, Instant now) {
    if (tournament.remoteStatus().isClosed()) return "Finished";
    if (tournament.secondsToStart().isPresent()) return "Starts in " + duration(tournament.secondsToStart().get());
    return tournament.startsAt().filter(now::isBefore).map(start -> "Starts in " + duration(Duration.between(now, start).toSeconds()))
        .orElse(tournament.remoteStatus().name());
  }

  private static String duration(long seconds) {
    long positive = Math.max(0, seconds);
    long minutes = positive / 60;
    return minutes > 0 ? minutes + " min" : positive + " sec";
  }

  private static String registration(ArenaTournamentRegistrationStatus status) {
    return switch (status) {
      case AVAILABLE -> "Available";
      case JOINING -> "Joining…";
      case JOINED -> "Joined";
      case NOT_ELIGIBLE -> "Not eligible";
      case CLOSED -> "Closed";
      case INCOMPATIBLE -> "Unsupported variant";
      case ERROR -> "Registration error";
    };
  }
}
