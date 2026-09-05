package com.escontrela.lastmove.application.training.storm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.domain.analysis.AnalysisContent;
import com.escontrela.lastmove.domain.analysis.AnalysisTree;
import com.escontrela.lastmove.domain.game.CastlingRights;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.game.PositionPiece;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.tactics.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class StormGameExerciseSelectorTest {
  @Test void returnsEmptyForAnEmptyOrUnsolvableGlobalPool() {
    TacticExercise exercise = new TacticExercise(TacticExerciseId.random(), "Empty",
        new AnalysisContent(position(), Optional.empty(), new AnalysisTree()), Instant.now(), Instant.now());
    StormGameExerciseSelector selector = new StormGameExerciseSelector(
        () -> List.of(new TacticExerciseReference(PlayerId.of(1L), TacticSuiteId.random(), exercise)), new Random(1));
    assertTrue(selector.next().isEmpty());
  }

  @Test void selectsAllEligibleOwnersWithoutRepeatingBeforeAReset() {
    TacticExercise a = exercise("A"), b = exercise("B");
    StormGameExerciseSelector selector = new StormGameExerciseSelector(() -> List.of(
        new TacticExerciseReference(PlayerId.of(1L), TacticSuiteId.random(), a),
        new TacticExerciseReference(PlayerId.of(2L), TacticSuiteId.random(), b)), new Random(4));
    assertEquals(2, List.of(selector.next().orElseThrow(), selector.next().orElseThrow()).stream()
        .map(challenge -> challenge.ownerId()).distinct().count());
  }

  @Test void neverRepeatsAnExerciseDuringOneSessionEvenAfterThePoolIsExhausted() {
    TacticExercise exercise = exercise("Only once");
    StormGameExerciseSelector selector = new StormGameExerciseSelector(
        () -> List.of(new TacticExerciseReference(PlayerId.of(1L), TacticSuiteId.random(), exercise)),
        new Random(4));

    assertTrue(selector.next().isPresent());
    assertTrue(selector.next().isEmpty());
  }

  private static TacticExercise exercise(String title) {
    AnalysisTree tree = new AnalysisTree();
    // A real solution is unnecessary here; the selector accepts only persisted exercises with roots.
    tree.addRoot(new com.escontrela.lastmove.domain.game.Ply(java.util.UUID.randomUUID(),
        new com.escontrela.lastmove.domain.game.MoveDescriptor(com.escontrela.lastmove.domain.common.Square.of("e2"), com.escontrela.lastmove.domain.common.Square.of("e4"), com.escontrela.lastmove.domain.notation.SanMove.of("e4"), false, false, false, Optional.empty()), position(), 1, PieceColor.WHITE));
    return new TacticExercise(TacticExerciseId.random(), title, new AnalysisContent(position(), Optional.empty(), tree), Instant.now(), Instant.now());
  }
  private static PositionSnapshot position() { return new PositionSnapshot(List.of(new PositionPiece(com.escontrela.lastmove.domain.common.Square.of("e1"), com.escontrela.lastmove.domain.common.PieceType.KING, PieceColor.WHITE), new PositionPiece(com.escontrela.lastmove.domain.common.Square.of("e8"), com.escontrela.lastmove.domain.common.PieceType.KING, PieceColor.BLACK)), PieceColor.WHITE, CastlingRights.none(), Optional.empty(), 0, 1, Optional.empty(), false, false, false); }
}
