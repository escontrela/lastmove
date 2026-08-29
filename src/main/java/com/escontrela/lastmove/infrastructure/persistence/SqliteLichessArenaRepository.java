package com.escontrela.lastmove.infrastructure.persistence;

import com.escontrela.lastmove.application.arena.*;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.GameId;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** SQLite implementation; every transition is durable before the corresponding API action. */
@Repository
public class SqliteLichessArenaRepository implements LichessArenaRepository {
  private final JdbcTemplate jdbc; private final PersistenceAvailability availability;
  public SqliteLichessArenaRepository(JdbcTemplate jdbc, PersistenceAvailability availability) { this.jdbc=jdbc; this.availability=availability; }
  public ArenaConnection connection() {
    if (!availability.isAvailable()) return disconnected();
    return jdbc.queryForList("SELECT * FROM lichess_arena_connections WHERE id=1").stream().findFirst().map(this::connection).orElseGet(this::disconnected);
  }
  public void saveConnection(ArenaConnection value) {
    if (!availability.isAvailable()) return;
    jdbc.update("INSERT INTO lichess_arena_connections(id,status,last_error,connected_at,disconnected_at,updated_at) VALUES(1,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET status=excluded.status,last_error=excluded.last_error,connected_at=excluded.connected_at,disconnected_at=excluded.disconnected_at,updated_at=excluded.updated_at", value.status().name(), value.lastError().orElse(null), epoch(value.connectedAt()), epoch(value.disconnectedAt()), epoch(value.updatedAt()));
  }
  public void saveChallenge(ArenaChallenge value) {
    if (!availability.isAvailable()) return;
    jdbc.update("INSERT INTO lichess_challenges(lichess_challenge_id,challenger_id,challenger_name,challenger_rating,variant,rated,clock_limit_seconds,clock_increment_seconds,decision,decision_reason,received_at,decided_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(lichess_challenge_id) DO UPDATE SET challenger_id=excluded.challenger_id,challenger_name=excluded.challenger_name,challenger_rating=excluded.challenger_rating,variant=excluded.variant,rated=excluded.rated,clock_limit_seconds=excluded.clock_limit_seconds,clock_increment_seconds=excluded.clock_increment_seconds,decision=excluded.decision,decision_reason=excluded.decision_reason,decided_at=excluded.decided_at,updated_at=excluded.updated_at", value.id(),value.challengerId().orElse(null),value.challengerName(),value.challengerRating().orElse(null),value.variant(),value.rated()?1:0,value.clockLimitSeconds().orElse(null),value.clockIncrementSeconds().orElse(null),value.decision().name(),value.decisionReason().orElse(null),epoch(value.receivedAt()),epoch(value.decidedAt()),epoch(value.updatedAt()));
  }
  public Optional<ArenaChallenge> findChallenge(String id) { return availability.isAvailable()?jdbc.queryForList("SELECT * FROM lichess_challenges WHERE lichess_challenge_id=?",id).stream().findFirst().map(this::challenge):Optional.empty(); }
  public List<ArenaChallenge> listChallenges() { return availability.isAvailable()?jdbc.queryForList("SELECT * FROM lichess_challenges ORDER BY updated_at DESC").stream().map(this::challenge).toList():List.of(); }
  @Transactional
  public synchronized boolean reserveChallenge(String id, int maximum) {
    if (!availability.isAvailable()) return false;
    int reserved=jdbc.queryForObject("SELECT COUNT(*) FROM lichess_challenges c WHERE decision IN ('RESERVED','ACCEPTED') AND NOT EXISTS (SELECT 1 FROM lichess_games g WHERE g.challenge_id=c.lichess_challenge_id AND g.remote_status IN ('STARTED','ACTIVE'))",Integer.class);
    int active=jdbc.queryForObject("SELECT COUNT(*) FROM lichess_games WHERE remote_status IN ('STARTED','ACTIVE')",Integer.class);
    if (reserved + active >= maximum) return false;
    return jdbc.update("UPDATE lichess_challenges SET decision='RESERVED',decision_reason=NULL,decided_at=?,updated_at=? WHERE lichess_challenge_id=? AND decision='RECEIVED'",now(),now(),id)==1;
  }
  public void saveGame(ArenaGame value) {
    if (!availability.isAvailable()) return;
    jdbc.update("INSERT INTO lichess_games(lichess_game_id,local_game_id,challenge_id,game_url,white_lichess_id,black_lichess_id,bot_color,remote_status,last_error,started_at,finished_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(lichess_game_id) DO UPDATE SET local_game_id=excluded.local_game_id,challenge_id=excluded.challenge_id,game_url=excluded.game_url,white_lichess_id=excluded.white_lichess_id,black_lichess_id=excluded.black_lichess_id,bot_color=excluded.bot_color,remote_status=excluded.remote_status,last_error=excluded.last_error,finished_at=excluded.finished_at,updated_at=excluded.updated_at",value.lichessGameId(),value.localGameId().map(g->g.value().toString()).orElse(null),value.challengeId().orElse(null),value.gameUrl().orElse(null),value.whiteLichessId().orElse(null),value.blackLichessId().orElse(null),value.botColor().map(Enum::name).orElse(null),value.status().name(),value.lastError().orElse(null),epoch(value.startedAt()),epoch(value.finishedAt()),epoch(value.updatedAt()));
  }
  public Optional<ArenaGame> findGame(String id) { return availability.isAvailable()?jdbc.queryForList("SELECT * FROM lichess_games WHERE lichess_game_id=?",id).stream().findFirst().map(this::game):Optional.empty(); }
  public List<ArenaGame> listActiveGames() { return availability.isAvailable()?jdbc.queryForList("SELECT * FROM lichess_games WHERE remote_status IN ('STARTED','ACTIVE','STREAM_CLOSED','ERROR') ORDER BY updated_at DESC").stream().map(this::game).toList():List.of(); }
  private ArenaConnection disconnected(){ Instant now=Instant.now(); return new ArenaConnection(ArenaConnectionStatus.DISCONNECTED,Optional.empty(),Optional.empty(),Optional.of(now),now); }
  private ArenaConnection connection(Map<String,Object> r){ return new ArenaConnection(ArenaConnectionStatus.valueOf((String)r.get("status")),opt((String)r.get("last_error")),instant(r.get("connected_at")),instant(r.get("disconnected_at")),Instant.ofEpochMilli(((Number)r.get("updated_at")).longValue())); }
  private ArenaChallenge challenge(Map<String,Object> r){ return new ArenaChallenge((String)r.get("lichess_challenge_id"),opt((String)r.get("challenger_id")),(String)r.get("challenger_name"),number(r.get("challenger_rating")),(String)r.get("variant"),((Number)r.get("rated")).intValue()!=0,number(r.get("clock_limit_seconds")),number(r.get("clock_increment_seconds")),ArenaChallengeDecision.valueOf((String)r.get("decision")),opt((String)r.get("decision_reason")),Instant.ofEpochMilli(((Number)r.get("received_at")).longValue()),instant(r.get("decided_at")),Instant.ofEpochMilli(((Number)r.get("updated_at")).longValue())); }
  private ArenaGame game(Map<String,Object> r){ return new ArenaGame((String)r.get("lichess_game_id"),opt((String)r.get("local_game_id")).map(v->new GameId(UUID.fromString(v))),opt((String)r.get("challenge_id")),opt((String)r.get("game_url")),opt((String)r.get("white_lichess_id")),opt((String)r.get("black_lichess_id")),opt((String)r.get("bot_color")).map(PieceColor::valueOf),ArenaGameStatus.valueOf((String)r.get("remote_status")),opt((String)r.get("last_error")),Instant.ofEpochMilli(((Number)r.get("started_at")).longValue()),instant(r.get("finished_at")),Instant.ofEpochMilli(((Number)r.get("updated_at")).longValue())); }
  private static long now(){return Instant.now().toEpochMilli();} private static Long epoch(Instant i){return i==null?null:i.toEpochMilli();} private static Long epoch(Optional<Instant> i){return i.map(Instant::toEpochMilli).orElse(null);} private static Optional<Instant> instant(Object v){return Optional.ofNullable((Number)v).map(n->Instant.ofEpochMilli(n.longValue()));} private static Optional<String> opt(String v){return Optional.ofNullable(v);} private static Optional<Integer> number(Object v){return Optional.ofNullable((Number)v).map(Number::intValue);}
}
