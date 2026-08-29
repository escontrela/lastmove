package com.escontrela.lastmove.infrastructure.persistence;

import com.escontrela.lastmove.application.repository.GameStatisticsRepository;
import com.escontrela.lastmove.application.statistics.GameStatisticsQuery;
import com.escontrela.lastmove.domain.statistics.GameResultCounts;
import com.escontrela.lastmove.domain.statistics.GameStatistics;
import com.escontrela.lastmove.domain.statistics.GameStatisticsBucket;
import com.escontrela.lastmove.domain.statistics.StatisticsGranularity;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQLite-only aggregate query; statistics are never persisted as their own data. */
@Repository
public class SqliteGameStatisticsRepository implements GameStatisticsRepository {
  private final JdbcTemplate jdbc;
  private final PersistenceAvailability availability;
  public SqliteGameStatisticsRepository(JdbcTemplate jdbc, PersistenceAvailability availability) { this.jdbc = jdbc; this.availability = availability; }

  @Override public GameStatistics statisticsFor(GameStatisticsQuery query) {
    if (!availability.isAvailable()) return new GameStatistics(List.of(), new GameResultCounts(0, 0, 0));
    long from = epochMillis(query.from()); long to = epochMillis(query.to().plusDays(1));
    String filter = query.engineId().isPresent() ? " AND c.engine_id=?" : "";
    Object[] parameters = query.engineId().isPresent()
        ? new Object[] {query.playerId().value(), query.playerId().value(), query.playerId().value(), from, to, query.engineId().orElseThrow()}
        : new Object[] {query.playerId().value(), query.playerId().value(), query.playerId().value(), from, to};
    String common = " FROM games g JOIN computer_game_configuration c ON c.game_id=g.id"
        + " WHERE (g.owner_player_id=? OR EXISTS (SELECT 1 FROM game_participants gp WHERE gp.game_id=g.id AND gp.player_id=?)"
        + " OR (c.engine_id='knightshade' AND EXISTS (SELECT 1 FROM players p WHERE p.id=? AND p.player_type='SYSTEM' AND p.external_provider='LICHESS')))"
        + " AND g.status='FINISHED' AND g.result IN ('WHITE_WINS','BLACK_WINS','DRAW')"
        + " AND g.updated_at>=? AND g.updated_at<?" + filter;
    String period = periodExpression(query.granularity());
    List<GameStatisticsBucket> buckets = jdbc.queryForList("SELECT " + period + " period_start, COUNT(*) games, "
            + "SUM(CASE WHEN (c.human_color='WHITE' AND g.result='WHITE_WINS') OR (c.human_color='BLACK' AND g.result='BLACK_WINS') THEN 1 ELSE 0 END) won, "
            + "SUM(CASE WHEN (c.human_color='WHITE' AND g.result='BLACK_WINS') OR (c.human_color='BLACK' AND g.result='WHITE_WINS') THEN 1 ELSE 0 END) lost, "
            + "SUM(CASE WHEN g.result='DRAW' THEN 1 ELSE 0 END) drawn" + common
            + " GROUP BY period_start ORDER BY period_start", parameters).stream()
        .map(row -> new GameStatisticsBucket(LocalDate.parse((String) row.get("period_start")), ((Number) row.get("games")).longValue(), new GameResultCounts(number(row.get("won")), number(row.get("lost")), number(row.get("drawn"))))).toList();
    Map<String, Object> outcomes = jdbc.queryForMap("SELECT "
        + "SUM(CASE WHEN (c.human_color='WHITE' AND g.result='WHITE_WINS') OR (c.human_color='BLACK' AND g.result='BLACK_WINS') THEN 1 ELSE 0 END) won, "
        + "SUM(CASE WHEN (c.human_color='WHITE' AND g.result='BLACK_WINS') OR (c.human_color='BLACK' AND g.result='WHITE_WINS') THEN 1 ELSE 0 END) lost, "
        + "SUM(CASE WHEN g.result='DRAW' THEN 1 ELSE 0 END) drawn" + common, parameters);
    return new GameStatistics(buckets, new GameResultCounts(number(outcomes.get("won")), number(outcomes.get("lost")), number(outcomes.get("drawn"))));
  }
  private static String periodExpression(StatisticsGranularity granularity) {
    return switch (granularity) {
      case DAY -> "date(g.updated_at / 1000, 'unixepoch', 'localtime')";
      case MONTH -> "date(g.updated_at / 1000, 'unixepoch', 'localtime', 'start of month')";
      case YEAR -> "date(g.updated_at / 1000, 'unixepoch', 'localtime', 'start of year')";
    };
  }
  private static long epochMillis(LocalDate value) { return value.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
  private static long number(Object value) { return value instanceof Number number ? number.longValue() : 0L; }
}
