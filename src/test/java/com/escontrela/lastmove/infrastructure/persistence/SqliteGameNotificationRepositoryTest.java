package com.escontrela.lastmove.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.escontrela.lastmove.domain.game.GameId;
import com.escontrela.lastmove.domain.player.PlayerId;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

class SqliteGameNotificationRepositoryTest {
  @TempDir Path tempDir;
  private SqliteGameNotificationRepository repository;
  private final PlayerId owner = PlayerId.of(1L);
  private final GameId game = new GameId(UUID.randomUUID());

  @BeforeEach void setUp() {
    DataSource source = DataSourceBuilder.create().driverClassName("org.sqlite.JDBC").url("jdbc:sqlite:" + tempDir.resolve("notifications.db")).build();
    Flyway.configure().dataSource(source).load().migrate();
    JdbcTemplate jdbc = new JdbcTemplate(source);
    jdbc.update("INSERT INTO players(id,email,firstname,lastname,created_at) VALUES(1,'notifications@example.com','Notify','Player',0)");
    repository = new SqliteGameNotificationRepository(jdbc, PersistenceAvailability.available());
  }

  @Test void replacesTheActiveNotificationWhenTheOpponentMovesAndWhenTheGameFinishes() {
    repository.notify(owner, game, "GAME_CREATED");
    repository.notify(owner, game, "OPPONENT_MOVED");
    assertEquals(List.of("OPPONENT_MOVED"), repository.findByOwner(owner).stream().map(notification -> notification.kind()).toList());
    repository.notify(owner, game, "GAME_FINISHED");
    assertEquals(List.of("GAME_FINISHED"), repository.findByOwner(owner).stream().map(notification -> notification.kind()).toList());
  }
}
