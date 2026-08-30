package com.escontrela.lastmove.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.arena.ArenaTournament;
import com.escontrela.lastmove.application.arena.ArenaTournamentRegistrationStatus;
import com.escontrela.lastmove.application.arena.ArenaTournamentStatus;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TournamentRowSummaryTest {
  private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

  @Test void exposesCompactTournamentDetailsAndAllowsAnAvailableRegistration() {
    TournamentRowSummary summary = TournamentRowSummary.from(tournament(ArenaTournamentRegistrationStatus.AVAILABLE), NOW);

    assertEquals("Knight Shade Bot Blitz", summary.title());
    assertEquals("3+2 · Rated · 1400–2400 · 18 players · Starts in 5 min", summary.details());
    assertEquals("Available", summary.registration());
    assertTrue(summary.canRequestRegistration());
  }

  @Test void disablesRegistrationWhileTheTournamentIsJoining() {
    TournamentRowSummary summary = TournamentRowSummary.from(tournament(ArenaTournamentRegistrationStatus.JOINING), NOW);

    assertEquals("Joining…", summary.registration());
    assertFalse(summary.canRequestRegistration());
  }

  @ParameterizedTest
  @MethodSource("registrationStates")
  void rendersEveryPersistedRegistrationState(ArenaTournamentRegistrationStatus state, String label, boolean canRegister) {
    TournamentRowSummary summary = TournamentRowSummary.from(tournament(state), NOW);
    assertEquals(label, summary.registration());
    assertEquals(canRegister, summary.canRequestRegistration());
  }

  private static Stream<Arguments> registrationStates() {
    return Stream.of(
        Arguments.of(ArenaTournamentRegistrationStatus.AVAILABLE, "Available", true),
        Arguments.of(ArenaTournamentRegistrationStatus.JOINED, "Joined", false),
        Arguments.of(ArenaTournamentRegistrationStatus.NOT_ELIGIBLE, "Not eligible", false),
        Arguments.of(ArenaTournamentRegistrationStatus.CLOSED, "Closed", false),
        Arguments.of(ArenaTournamentRegistrationStatus.INCOMPATIBLE, "Unsupported variant", false),
        Arguments.of(ArenaTournamentRegistrationStatus.ERROR, "Registration error", false));
  }

  private static ArenaTournament tournament(ArenaTournamentRegistrationStatus registration) {
    return new ArenaTournament("botblitz", "Knight Shade Bot Blitz", ArenaTournamentStatus.CREATED, "standard", true,
        180, 2, 60, 18, Optional.of(1400), Optional.of(2400), true,
        Optional.of(NOW.plusSeconds(300)), Optional.empty(), Optional.of(300),
        Optional.of("https://lichess.org/tournament/botblitz"), registration, Optional.empty(), NOW, NOW);
  }
}
