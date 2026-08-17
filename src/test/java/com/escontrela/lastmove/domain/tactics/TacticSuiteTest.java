package com.escontrela.lastmove.domain.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.domain.analysis.AnalysisDocument;
import com.escontrela.lastmove.domain.analysis.AnalysisDocumentFactory;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.CastlingRights;
import com.escontrela.lastmove.domain.game.MoveDescriptor;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.PositionPiece;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.notation.SanMove;
import com.escontrela.lastmove.domain.player.PlayerId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TacticSuiteTest {

  private final AnalysisDocumentFactory documentFactory = new AnalysisDocumentFactory();
  private final TacticExerciseFactory exerciseFactory = new TacticExerciseFactory(documentFactory);

  @Test
  void validatesSuiteTitleAndMaintainsExerciseOrder() {
    assertThrows(NullPointerException.class, () -> TacticSuite.create(null, "Forks"));
    assertThrows(IllegalArgumentException.class, () -> TacticSuite.create(PlayerId.of(1L), " "));

    TacticSuite suite = TacticSuite.create(PlayerId.of(1L), "  Tactical motifs  ");
    TacticExercise first = exerciseFactory.empty("Fork", whiteToMove());
    TacticExercise second = exerciseFactory.empty("Pin", whiteToMove());
    suite.addExercise(first);
    suite.addExercise(second);

    assertEquals("Tactical motifs", suite.title());
    assertTrue(suite.moveExercise(second.id(), -1));
    assertEquals(List.of("Pin", "Fork"), suite.exercises().stream().map(TacticExercise::title).toList());
    assertFalse(suite.moveExercise(second.id(), -1));
    assertTrue(suite.removeExercise(first.id()));
    assertTrue(suite.exercise(first.id()).isEmpty());
  }

  @Test
  void derivesSolverColorFromPositionRatherThanBoardPresentation() {
    TacticExercise exercise = exerciseFactory.empty("Black to move", blackToMove());

    assertEquals(PieceColor.BLACK, exercise.solverColor());
    assertFalse(exercise.hasSolution());
  }

  @Test
  void copiesAnalysisSourceSoFutureEditsCannotChangeTheExerciseSolution() {
    AnalysisDocument source = documentFactory.fromPosition(whiteToMove(), Optional.empty());
    source.apply(
        MoveExecutionResult.accepted(
            whiteToMove(),
            new MoveDescriptor(
                Square.of("e2"), Square.of("e4"), SanMove.of("e4"), false, false, false, Optional.empty())));

    TacticExercise exercise = exerciseFactory.fromDocument("Find e4", source);
    source.first();

    assertTrue(exercise.hasSolution());
    assertEquals("e4", exercise.solution().tree().roots().getFirst().ply().move().san().getValue());
    assertFalse(exercise.solution().tree().roots().getFirst().id().equals(source.content().tree().roots().getFirst().id()));
  }

  private PositionSnapshot whiteToMove() {
    return position(PieceColor.WHITE);
  }

  private PositionSnapshot blackToMove() {
    return position(PieceColor.BLACK);
  }

  private PositionSnapshot position(PieceColor activeColor) {
    return new PositionSnapshot(
        List.of(
            new PositionPiece(Square.of("e1"), PieceType.KING, PieceColor.WHITE),
            new PositionPiece(Square.of("e8"), PieceType.KING, PieceColor.BLACK),
            new PositionPiece(Square.of("e2"), PieceType.PAWN, PieceColor.WHITE)),
        activeColor,
        CastlingRights.none(),
        Optional.empty(),
        0,
        1,
        Optional.empty(),
        false,
        false,
        false);
  }
}
