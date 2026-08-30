package com.escontrela.lastmove.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.escontrela.lastmove.application.arena.ArenaTournament;
import com.escontrela.lastmove.application.arena.ArenaTournamentRegistrationStatus;
import com.escontrela.lastmove.application.arena.ArenaTournamentStatus;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

class SqliteLichessArenaRepositoryTest {
  @TempDir Path tempDir;
  private SqliteLichessArenaRepository repository;

  @BeforeEach void setUp() {
    DataSource source = DataSourceBuilder.create().driverClassName("org.sqlite.JDBC").url("jdbc:sqlite:" + tempDir.resolve("arena.db")).build();
    Flyway.configure().dataSource(source).load().migrate();
    repository = new SqliteLichessArenaRepository(new JdbcTemplate(source), PersistenceAvailability.available());
  }

  @Test void upsertsTournamentRegistrationAndPreservesTheLatestSnapshot() {
    Instant first = Instant.parse("2026-08-30T10:00:00Z");
    ArenaTournament initial = tournament(3, ArenaTournamentRegistrationStatus.AVAILABLE, first);
    repository.saveTournament(initial);
    ArenaTournament updated = new ArenaTournament("botblitz", "Bot Blitz", ArenaTournamentStatus.STARTED, "standard", true,
        180, 2, 60, 15, Optional.of(1400), Optional.of(2400), true, Optional.of(first), Optional.empty(), Optional.empty(),
        Optional.of("https://lichess.org/tournament/botblitz"), ArenaTournamentRegistrationStatus.JOINING, Optional.empty(), first,
        first.plusSeconds(30));
    repository.saveTournament(updated);

    ArenaTournament restored = repository.findTournament("botblitz").orElseThrow();

    assertEquals(15, restored.playerCount());
    assertEquals(ArenaTournamentStatus.STARTED, restored.remoteStatus());
    assertEquals(ArenaTournamentRegistrationStatus.JOINING, restored.registrationStatus());
    assertEquals(1, repository.listTournaments().size());
  }

  private static ArenaTournament tournament(int players, ArenaTournamentRegistrationStatus registration, Instant now) {
    return new ArenaTournament("botblitz", "Bot Blitz", ArenaTournamentStatus.CREATED, "standard", true, 180, 2, 60, players,
        Optional.of(1400), Optional.of(2400), true, Optional.of(now), Optional.empty(), Optional.of(60),
        Optional.of("https://lichess.org/tournament/botblitz"), registration, Optional.empty(), now, now);
  }
}
