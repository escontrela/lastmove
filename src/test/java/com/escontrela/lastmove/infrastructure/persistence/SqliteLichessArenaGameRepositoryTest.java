package com.escontrela.lastmove.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.escontrela.lastmove.application.arena.*;
import com.escontrela.lastmove.domain.common.PieceColor;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

class SqliteLichessArenaGameRepositoryTest {
  @TempDir Path tempDir;
  private SqliteLichessArenaRepository repository;

  @BeforeEach void setUp() {
    var source = DataSourceBuilder.create().driverClassName("org.sqlite.JDBC").url("jdbc:sqlite:" + tempDir.resolve("arena.db")).build();
    Flyway.configure().dataSource(source).load().migrate();
    repository = new SqliteLichessArenaRepository(new JdbcTemplate(source), PersistenceAvailability.available());
  }

  @Test void persistsTournamentLinkAndUpsertsTheSameLichessGame() {
    Instant now = Instant.parse("2026-08-30T12:00:00Z");
    ArenaGame started = new ArenaGame("game-1", Optional.of(new com.escontrela.lastmove.domain.game.GameId(UUID.randomUUID())),
        Optional.empty(), Optional.of("tournament-1"), Optional.of("https://lichess.org/game-1"), Optional.of("white"),
        Optional.of("black"), Optional.of(PieceColor.WHITE), ArenaGameStatus.STARTED, Optional.empty(), now, Optional.empty(), now);
    repository.saveGame(started);
    repository.saveGame(new ArenaGame(started.lichessGameId(), started.localGameId(), started.challengeId(), started.tournamentId(),
        started.gameUrl(), started.whiteLichessId(), started.blackLichessId(), started.botColor(), ArenaGameStatus.ACTIVE,
        Optional.empty(), started.startedAt(), Optional.empty(), now.plusSeconds(2)));

    ArenaGame restored = repository.findGame("game-1").orElseThrow();
    assertEquals(Optional.of("tournament-1"), restored.tournamentId());
    assertEquals(Optional.of("https://lichess.org/game-1"), restored.gameUrl());
    assertEquals(ArenaGameStatus.ACTIVE, restored.status());
  }
}
