package com.escontrela.lastmove.infrastructure.game;

import com.escontrela.lastmove.application.repository.ProgressiveGameRepository;
import com.escontrela.lastmove.domain.game.ChessGame;
import com.escontrela.lastmove.domain.game.GameId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Process-local progressive-game repository that can later be replaced by durable storage. */
@Repository
public final class InMemoryProgressiveGameRepository implements ProgressiveGameRepository {

  private final Map<GameId, ChessGame> games = new LinkedHashMap<>();

  @Override
  public synchronized void save(ChessGame game) {
    ChessGame required = Objects.requireNonNull(game, "game must not be null");
    games.put(required.id(), required);
  }

  @Override
  public synchronized Optional<ChessGame> findById(GameId gameId) {
    return Optional.ofNullable(games.get(Objects.requireNonNull(gameId, "gameId must not be null")));
  }

  @Override
  public synchronized List<ChessGame> findAll() {
    return List.copyOf(games.values());
  }

  @Override
  public synchronized boolean deleteById(GameId gameId) {
    return games.remove(Objects.requireNonNull(gameId, "gameId must not be null")) != null;
  }
}
