package com.escontrela.lastmove.domain.study;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.domain.analysis.AnalysisDocument;
import com.escontrela.lastmove.domain.analysis.AnalysisDocumentFactory;
import com.escontrela.lastmove.domain.analysis.AnalysisOrigin;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.CastlingRights;
import com.escontrela.lastmove.domain.game.PositionPiece;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StudyTest {

  private final AnalysisDocumentFactory documentFactory = new AnalysisDocumentFactory();
  private final StudyChapterFactory chapterFactory = new StudyChapterFactory(documentFactory);

  @Test
  void createRequiresOwnerAndTitle() {
    assertThrows(NullPointerException.class, () -> Study.create(null, "Openings"));
    assertThrows(IllegalArgumentException.class, () -> Study.create(PlayerId.of(1L), "  "));
    assertThrows(NullPointerException.class, () -> Study.create(PlayerId.of(1L), null));
  }

  @Test
  void addsRenamesRemovesAndMovesChaptersWithoutBreakingOrder() {
    Study study = Study.create(PlayerId.of(1L), "My repertoire");
    StudyChapter first = chapterFactory.fromPosition("White", AnalysisOrigin.INITIAL_POSITION, initialPosition(), Optional.empty());
    StudyChapter second = chapterFactory.fromPosition("Black", AnalysisOrigin.INITIAL_POSITION, initialPosition(), Optional.empty());
    StudyChapter third = chapterFactory.fromPosition("Endings", AnalysisOrigin.INITIAL_POSITION, initialPosition(), Optional.empty());

    study.addChapter(first);
    study.addChapter(second);
    study.addChapter(third);

    assertEquals(List.of("White", "Black", "Endings"), titles(study));

    assertTrue(study.moveChapter(third.id(), -1));
    assertEquals(List.of("White", "Endings", "Black"), titles(study));
    assertTrue(study.moveChapter(third.id(), 1));
    assertEquals(List.of("White", "Black", "Endings"), titles(study));
    assertFalse(study.moveChapter(first.id(), -1));

    first.rename("Openings for White");
    assertEquals("Openings for White", study.chapter(first.id()).orElseThrow().title());

    assertTrue(study.removeChapter(second.id()));
    assertFalse(study.removeChapter(second.id()));
    assertTrue(study.chapter(second.id()).isEmpty());
    assertEquals(List.of("Openings for White", "Endings"), titles(study));
  }

  @Test
  void renameAndDescriptionUpdateMetadataOnly() {
    Study study = Study.create(PlayerId.of(1L), "Opening studies");
    study.rename("  Midgame studies  ");
    study.setDescription(Optional.of("Collected middlegame ideas"));

    assertEquals("Midgame studies", study.title());
    assertEquals("Collected middlegame ideas", study.description().orElseThrow());
    assertEquals(PlayerId.of(1L), study.ownerId());
  }

  @Test
  void chapterArchivesAnIndependentCopyOfItsSourceDocument() {
    AnalysisDocument source = documentFactory.fromPosition(initialPosition(), Optional.empty());
    source.apply(
        com.escontrela.lastmove.domain.game.MoveExecutionResult.accepted(
            initialPosition(),
            new com.escontrela.lastmove.domain.game.MoveDescriptor(
                Square.of("e2"),
                Square.of("e4"),
                com.escontrela.lastmove.domain.notation.SanMove.of("e4"),
                false,
                false,
                false,
                Optional.empty())));

    StudyChapter chapter = chapterFactory.fromDocument("Copied", AnalysisOrigin.INITIAL_POSITION, source);

    assertEquals(1, chapter.document().rootVariations().size());
    chapter.document().first();
    assertTrue(chapter.document().currentPly().isEmpty());
    assertEquals(1, source.rootVariations().size());
    assertFalse(chapter.document().rootVariations().getFirst().id().equals(source.rootVariations().getFirst().id()));
  }

  private List<String> titles(Study study) {
    return study.chapters().stream().map(StudyChapter::title).toList();
  }

  private PositionSnapshot initialPosition() {
    return new PositionSnapshot(
        List.of(new PositionPiece(Square.of("e1"), PieceType.KING, PieceColor.WHITE)),
        PieceColor.WHITE,
        CastlingRights.initial(),
        Optional.empty(),
        0,
        1,
        Optional.empty(),
        false,
        false,
        false);
  }
}