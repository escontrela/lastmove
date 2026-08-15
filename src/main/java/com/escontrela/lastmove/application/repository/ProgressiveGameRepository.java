package com.escontrela.lastmove.application.repository;

import com.escontrela.lastmove.domain.game.ChessGame;
import com.escontrela.lastmove.domain.game.GameId;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for authoritative progressive chess-game aggregates. */
public interface ProgressiveGameRepository {

  void save(ChessGame game);

  Optional<ChessGame> findById(GameId gameId);

  List<ChessGame> findAll();

  boolean deleteById(GameId gameId);
}
