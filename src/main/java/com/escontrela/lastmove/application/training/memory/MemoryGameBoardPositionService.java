package com.escontrela.lastmove.application.training.memory;

import com.escontrela.lastmove.domain.game.ChessRulesEngine;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.notation.Fen;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Maps the application's FEN DTO to the engine-neutral snapshot consumed by the board control. */
@Service
public final class MemoryGameBoardPositionService {
  private final ChessRulesEngine rulesEngine;

  public MemoryGameBoardPositionService(ChessRulesEngine rulesEngine) {
    this.rulesEngine = Objects.requireNonNull(rulesEngine, "rulesEngine must not be null");
  }

  public PositionSnapshot snapshot(MemoryGamePosition position) {
    Objects.requireNonNull(position, "position must not be null");
    return rulesEngine.positionFrom(Fen.of(position.fen()));
  }
}
