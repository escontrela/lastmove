package com.escontrela.lastmove.application.repository;

import com.escontrela.lastmove.application.game.SavedGame;
import com.escontrela.lastmove.application.game.SavedGameContext;
import com.escontrela.lastmove.application.game.SavedGameSummary;
import com.escontrela.lastmove.domain.game.ChessGame;
import com.escontrela.lastmove.domain.game.GameId;
import com.escontrela.lastmove.domain.player.PlayerId;
import java.util.List;
import java.util.Optional;

/** Generic persistence boundary for chess games independently from their play mode. */
public interface SavedGameRepository {
  void save(ChessGame game, SavedGameContext context);
  Optional<SavedGame> findSaved(GameId gameId);
  List<SavedGameSummary> listSummaries(PlayerId ownerId);
  boolean deleteById(GameId gameId);
}
