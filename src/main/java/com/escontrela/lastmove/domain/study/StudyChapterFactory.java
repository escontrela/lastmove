package com.escontrela.lastmove.domain.study;

import com.escontrela.lastmove.domain.analysis.AnalysisDocument;
import com.escontrela.lastmove.domain.analysis.AnalysisDocumentFactory;
import com.escontrela.lastmove.domain.analysis.AnalysisOrigin;
import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.game.ImportedPgnGame;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Domain factory that builds {@link StudyChapter} values.
 *
 * <p>Chapters can start from a position, import a PGN tree, or archive a snapshot of an analysis
 * document. Archiving always deep-copies the source so the ephemeral session and the persisted
 * chapter never share mutable state.
 */
public final class StudyChapterFactory {

  private final AnalysisDocumentFactory documentFactory;

  public StudyChapterFactory(AnalysisDocumentFactory documentFactory) {
    this.documentFactory =
        Objects.requireNonNull(documentFactory, "documentFactory must not be null");
  }

  /** Creates an empty chapter at the supplied position. */
  public StudyChapter fromPosition(
      String title,
      AnalysisOrigin origin,
      PositionSnapshot initialPosition,
      Optional<GameResult> sourceResult) {
    AnalysisDocument document =
        documentFactory.fromPosition(
            Objects.requireNonNull(initialPosition, "initialPosition must not be null"),
            Objects.requireNonNull(sourceResult, "sourceResult must not be null"));
    return newChapter(title, origin, document);
  }

  /** Creates a chapter whose move tree comes from an imported PGN game. */
  public StudyChapter fromImportedPgn(
      String title, ImportedPgnGame importedGame, PositionSnapshot initialPosition) {
    AnalysisDocument document =
        documentFactory.fromImportedPgn(
            Objects.requireNonNull(importedGame, "importedGame must not be null"),
            Objects.requireNonNull(initialPosition, "initialPosition must not be null"));
    return newChapter(title, AnalysisOrigin.PGN, document);
  }

  /** Archives an independent deep copy of the supplied analysis document as a new chapter. */
  public StudyChapter fromDocument(String title, AnalysisOrigin origin, AnalysisDocument source) {
    AnalysisDocument copy =
        documentFactory.copyOf(
            Objects.requireNonNull(source, "source must not be null"));
    return newChapter(title, Objects.requireNonNull(origin, "origin must not be null"), copy);
  }

  private StudyChapter newChapter(String title, AnalysisOrigin origin, AnalysisDocument document) {
    Instant now = Instant.now();
    return new StudyChapter(
        StudyChapterId.random(), title, origin, document, now, now);
  }
}
