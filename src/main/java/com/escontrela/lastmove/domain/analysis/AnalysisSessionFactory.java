package com.escontrela.lastmove.domain.analysis;

import com.escontrela.lastmove.domain.game.GameRecord;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import java.util.Objects;

/**
 * Domain factory that copies played-game records into independent analysis sessions.
 *
 * <p>The official game line becomes the preferred analysis line. New variations added to the
 * resulting session never mutate the source {@link GameRecord} or its original game aggregate.
 */
public final class AnalysisSessionFactory {

  /** Creates a navigable study from an immutable progressive-game record. */
  public AnalysisSession fromGame(GameRecord gameRecord) {
    GameRecord record = Objects.requireNonNull(gameRecord, "gameRecord must not be null");
    AnalysisSession session =
        new AnalysisSession(
            AnalysisSessionId.random(),
            record.title(),
            AnalysisOrigin.PLAYED_GAME,
            record.initialPosition(),
            record.result());
    record.moves().forEach(
        recorded ->
            session.apply(
                MoveExecutionResult.accepted(
                    recorded.ply().resultingPosition(), recorded.ply().move())));
    session.first();
    if (!record.moves().isEmpty()) {
      session.next();
    }
    return session;
  }
}
