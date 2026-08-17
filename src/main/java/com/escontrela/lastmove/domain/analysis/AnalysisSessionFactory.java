package com.escontrela.lastmove.domain.analysis;

import com.escontrela.lastmove.domain.game.GameRecord;
import java.util.Objects;

/**
 * Domain factory that copies played-game records into independent analysis sessions.
 *
 * <p>The official game line becomes the preferred analysis line. New variations added to the
 * resulting session never mutate the source {@link GameRecord} or its original game aggregate.
 */
public final class AnalysisSessionFactory {

  private final AnalysisDocumentFactory documentFactory;

  public AnalysisSessionFactory() {
    this(new AnalysisDocumentFactory());
  }

  public AnalysisSessionFactory(AnalysisDocumentFactory documentFactory) {
    this.documentFactory =
        Objects.requireNonNull(documentFactory, "documentFactory must not be null");
  }

  /** Creates a navigable study from an immutable progressive-game record. */
  public AnalysisSession fromGame(GameRecord gameRecord) {
    GameRecord record = Objects.requireNonNull(gameRecord, "gameRecord must not be null");
    return new AnalysisSession(
        AnalysisSessionId.random(),
        record.title(),
        AnalysisOrigin.PLAYED_GAME,
        documentFactory.fromGame(record));
  }
}
