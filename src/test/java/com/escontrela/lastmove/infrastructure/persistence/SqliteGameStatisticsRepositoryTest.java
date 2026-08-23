package com.escontrela.lastmove.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.escontrela.lastmove.application.statistics.GameStatisticsQuery;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.statistics.GameStatistics;
import com.escontrela.lastmove.domain.statistics.StatisticsGranularity;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

class SqliteGameStatisticsRepositoryTest {
  @TempDir Path tempDir;
  private JdbcTemplate jdbc;
  private SqliteGameStatisticsRepository repository;

  @BeforeEach void setUp() {
    DataSource source = DataSourceBuilder.create().driverClassName("org.sqlite.JDBC").url("jdbc:sqlite:" + tempDir.resolve("statistics.db")).build();
    Flyway.configure().dataSource(source).load().migrate(); jdbc = new JdbcTemplate(source);
    jdbc.update("INSERT INTO players(id,email,firstname,lastname,created_at) VALUES(1,'stats@example.com','Stats','Player',?)", instant("2026-01-01"));
    repository = new SqliteGameStatisticsRepository(jdbc, PersistenceAvailability.available());
  }

  @Test void groupsOnlyFinishedComputerGamesAndCalculatesResultsFromTheHumanColor() {
    insertGame("one", "2026-01-04", "WHITE_WINS", "WHITE", "sunfish");
    insertGame("two", "2026-01-17", "BLACK_WINS", "BLACK", "sunfish");
    insertGame("three", "2026-02-01", "DRAW", "WHITE", "maia-1500");
    insertGame("unfinished", "2026-01-15", null, "WHITE", "sunfish");
    GameStatistics all = repository.statisticsFor(query(Optional.empty()));
    assertEquals(3, all.results().total()); assertEquals(2, all.results().won()); assertEquals(0, all.results().lost()); assertEquals(1, all.results().drawn());
    assertEquals(2, all.buckets().size()); assertEquals(2, all.buckets().getFirst().games());
    assertEquals(2, all.buckets().getFirst().results().won());
    GameStatistics sunfish = repository.statisticsFor(query(Optional.of("sunfish")));
    assertEquals(2, sunfish.results().won()); assertEquals(0, sunfish.results().drawn()); assertEquals(1, sunfish.buckets().size());
  }

  private GameStatisticsQuery query(Optional<String> engine) { return new GameStatisticsQuery(PlayerId.of(1L), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28), StatisticsGranularity.MONTH, engine); }
  private void insertGame(String id, String date, String result, String color, String engine) {
    String status = result == null ? "IN_PROGRESS" : "FINISHED"; long updated = instant(date);
    jdbc.update("INSERT INTO games(id,owner_player_id,game_type,status,initial_fen,current_fen,white_name,black_name,time_control_increment_ms,result,created_at,updated_at) VALUES(?,1,'HUMAN_VS_COMPUTER',?,'fen','fen','White','Black',0,?,?,?)", id,status,result,updated,updated);
    jdbc.update("INSERT INTO computer_game_configuration(game_id,human_name,human_color,engine_id,engine_thinking_ms) VALUES(?,'Stats',?,?,500)", id,color,engine);
  }
  private static long instant(String date) { return LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
}
