package com.escontrela.lastmove.infrastructure.persistence;

import com.escontrela.lastmove.application.notification.GameNotification;
import com.escontrela.lastmove.application.notification.GameNotificationRepository;
import com.escontrela.lastmove.domain.game.GameId;
import com.escontrela.lastmove.domain.player.PlayerId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SqliteGameNotificationRepository implements GameNotificationRepository {
  private final JdbcTemplate jdbc; private final PersistenceAvailability availability;
  public SqliteGameNotificationRepository(JdbcTemplate jdbc, PersistenceAvailability availability) { this.jdbc=jdbc; this.availability=availability; }
  public void notify(PlayerId owner, GameId game, String kind) { if (!availability.isAvailable()) return; jdbc.update("INSERT INTO game_notifications(id,owner_player_id,game_id,kind,created_at) VALUES(?,?,?,?,?)", UUID.randomUUID().toString(),owner.value(),game.value().toString(),kind,Instant.now().toEpochMilli()); }
  public List<GameNotification> findByOwner(PlayerId owner) { if(!availability.isAvailable()) return List.of(); return jdbc.query("SELECT * FROM game_notifications WHERE owner_player_id=? ORDER BY created_at DESC",(rs,n)->new GameNotification(UUID.fromString(rs.getString("id")),new GameId(UUID.fromString(rs.getString("game_id"))),rs.getString("kind"),Instant.ofEpochMilli(rs.getLong("created_at")),rs.getObject("read_at")==null),owner.value()); }
  public boolean hasUnread(PlayerId owner) { return availability.isAvailable() && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM game_notifications WHERE owner_player_id=? AND read_at IS NULL)",Boolean.class,owner.value())); }
  public void deleteById(PlayerId owner, UUID id) { if(availability.isAvailable()) jdbc.update("DELETE FROM game_notifications WHERE id=? AND owner_player_id=?",id.toString(),owner.value()); }
  public void deleteAll(PlayerId owner) { if(availability.isAvailable()) jdbc.update("DELETE FROM game_notifications WHERE owner_player_id=?",owner.value()); }
}
