package com.escontrela.lastmove.domain.tactics;

import com.escontrela.lastmove.domain.analysis.AnalysisContent;
import com.escontrela.lastmove.domain.analysis.AnalysisDocument;
import com.escontrela.lastmove.domain.analysis.AnalysisDocumentFactory;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.game.ImportedPgnGame;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Builds exercises as independent copies of an analysis position and its accepted variations. */
public final class TacticExerciseFactory {

  private final AnalysisDocumentFactory documentFactory;

  public TacticExerciseFactory(AnalysisDocumentFactory documentFactory) {
    this.documentFactory = Objects.requireNonNull(documentFactory, "documentFactory must not be null");
  }

  public TacticExercise empty(String title, PositionSnapshot position) {
    AnalysisDocument document = documentFactory.fromPosition(position, Optional.empty());
    return exercise(title, document.content());
  }

  /** Copies both the position and solution tree, keeping the source study/session independent. */
  public TacticExercise fromDocument(String title, AnalysisDocument source) {
    return exercise(title, documentFactory.copyOf(source).content());
  }

  /** Builds a tactic whose accepted solution tree is the complete PGN move tree. */
  public TacticExercise fromImportedPgn(
      String title, ImportedPgnGame importedGame, PositionSnapshot initialPosition) {
    return exercise(
        title,
        documentFactory
            .fromImportedPgn(
                Objects.requireNonNull(importedGame, "importedGame must not be null"),
                Objects.requireNonNull(initialPosition, "initialPosition must not be null"))
            .content());
  }

  /** Copies the active study variation as a standalone tactic solution. */
  public TacticExercise fromSelectedLine(String title, AnalysisDocument source) {
    return exercise(title, documentFactory.copySelectedLine(source).content());
  }

  private TacticExercise exercise(String title, AnalysisContent content) {
    Instant now = Instant.now();
    return new TacticExercise(TacticExerciseId.random(), title, content, now, now);
  }
}
