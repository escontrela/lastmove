package com.escontrela.lastmove.infrastructure.game;

import com.escontrela.lastmove.application.game.GameType;
import com.escontrela.lastmove.application.game.SavedGame;
import com.escontrela.lastmove.application.game.SavedGameContext;
import com.escontrela.lastmove.application.game.SavedGameSummary;
import com.escontrela.lastmove.application.repository.ProgressiveGameRepository;
import com.escontrela.lastmove.domain.game.ChessGame;
import com.escontrela.lastmove.domain.game.GameId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.Instant;
import java.util.Optional;

/** Process-local progressive-game repository that can later be replaced by durable storage. */
public class InMemoryProgressiveGameRepository implements ProgressiveGameRepository {

  private final Map<GameId, SavedGame> games = new LinkedHashMap<>();

  public synchronized void save(ChessGame game) {
    ChessGame required = Objects.requireNonNull(game, "game must not be null");
    SavedGame previous = games.get(required.id());
    save(required, previous == null ? legacyContext() : previous.context());
  }

  public synchronized Optional<ChessGame> findById(GameId gameId) {
    return findSaved(gameId).map(SavedGame::game);
  }

  public synchronized List<ChessGame> findAll() {
    return games.values().stream().map(SavedGame::game).toList();
  }

  @Override
  public synchronized boolean deleteById(GameId gameId) {
    return games.remove(Objects.requireNonNull(gameId, "gameId must not be null")) != null;
  }

  @Override
  public synchronized void save(ChessGame game, SavedGameContext context) {
    ChessGame required = Objects.requireNonNull(game, "game must not be null");
    games.put(required.id(), new SavedGame(required, Objects.requireNonNull(context, "context must not be null")));
  }

  @Override
  public synchronized Optional<SavedGame> findSaved(GameId gameId) {
    return Optional.ofNullable(games.get(Objects.requireNonNull(gameId, "gameId must not be null")));
  }

  @Override
  public synchronized List<SavedGameSummary> listSummaries(com.escontrela.lastmove.domain.player.PlayerId ownerId) {
    return games.values().stream()
        .filter(saved -> saved.context().participantPlayerIds().contains(ownerId)
            || saved.context().ownerPlayerId().filter(ownerId::equals).isPresent())
        .map(saved -> new SavedGameSummary(saved.game().id(), saved.context().gameType(),
            saved.game().whitePlayer().map(com.escontrela.lastmove.domain.game.GamePlayer::getName).orElse("White"),
            saved.game().blackPlayer().map(com.escontrela.lastmove.domain.game.GamePlayer::getName).orElse("Black"),
            saved.game().result().isPresent(), saved.game().result(), saved.game().moveHistory().size(), Instant.now()))
        .toList();
  }

  private static SavedGameContext legacyContext() {
    return new SavedGameContext(GameType.HUMAN_VS_COMPUTER, Optional.empty(),
        Optional.of(new com.escontrela.lastmove.application.computer.ComputerGameConfiguration(
            "Human", com.escontrela.lastmove.domain.common.PieceColor.WHITE,
            com.escontrela.lastmove.domain.game.TimeControl.unlimited(), "legacy", java.time.Duration.ofSeconds(1))));
  }
}
