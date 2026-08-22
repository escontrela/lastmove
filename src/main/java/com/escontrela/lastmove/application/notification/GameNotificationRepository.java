package com.escontrela.lastmove.application.notification;

import com.escontrela.lastmove.domain.game.GameId;
import com.escontrela.lastmove.domain.player.PlayerId;
import java.util.List;

public interface GameNotificationRepository {
  void notify(PlayerId owner, GameId gameId, String kind);
  List<GameNotification> findByOwner(PlayerId owner);
  boolean hasUnread(PlayerId owner);
  void deleteById(PlayerId owner, java.util.UUID id);
  void deleteAll(PlayerId owner);
}
