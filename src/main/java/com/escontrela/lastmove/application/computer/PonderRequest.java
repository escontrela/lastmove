package com.escontrela.lastmove.application.computer;

import com.escontrela.lastmove.domain.game.GameId;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.util.List;
import java.util.Objects;

/** Immutable request to predict the opponent's reply and prepare a continuation for one turn. */
public record PonderRequest(
    GameId gameId,
    long generation,
    PositionSnapshot positionAfterOurMove,
    List<PositionSnapshot> positionHistory,
    PonderSettings settings,
    String evaluatorConfigurationId,
    boolean shareWithSameGameRealSearch) {

  public PonderRequest(GameId gameId, long generation, PositionSnapshot positionAfterOurMove,
      List<PositionSnapshot> positionHistory, PonderSettings settings,
      String evaluatorConfigurationId) {
    this(gameId, generation, positionAfterOurMove, positionHistory, settings,
        evaluatorConfigurationId, false);
  }

  public PonderRequest {
    Objects.requireNonNull(gameId, "gameId must not be null");
    if (generation < 0) throw new IllegalArgumentException("generation must not be negative");
    Objects.requireNonNull(positionAfterOurMove, "positionAfterOurMove must not be null");
    positionHistory = List.copyOf(Objects.requireNonNull(positionHistory, "positionHistory must not be null"));
    Objects.requireNonNull(settings, "settings must not be null");
    evaluatorConfigurationId = Objects.requireNonNull(evaluatorConfigurationId,
        "evaluatorConfigurationId must not be null").trim();
    if (evaluatorConfigurationId.isEmpty()) {
      throw new IllegalArgumentException("evaluatorConfigurationId must not be blank");
    }
  }
}
