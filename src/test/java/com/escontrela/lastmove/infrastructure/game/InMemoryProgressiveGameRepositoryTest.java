package com.escontrela.lastmove.infrastructure.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.domain.game.Player;
import com.escontrela.lastmove.domain.game.TimeControl;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoRulesEngine;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InMemoryProgressiveGameRepositoryTest {

  @Test
  void storesFindsListsAndDeletesProgressiveGames() {
    var repository = new InMemoryProgressiveGameRepository();
    var game =
        new ChessGameFactory(new ChesspressoRulesEngine())
            .createInitial(
                new Player("Alice", PieceColor.WHITE),
                new Player("Bob", PieceColor.BLACK),
                Optional.of(TimeControl.unlimited()));

    repository.save(game);

    assertEquals(game, repository.findById(game.id()).orElseThrow());
    assertEquals(java.util.List.of(game), repository.findAll());
    assertTrue(repository.deleteById(game.id()));
    assertTrue(repository.findAll().isEmpty());
  }
}
