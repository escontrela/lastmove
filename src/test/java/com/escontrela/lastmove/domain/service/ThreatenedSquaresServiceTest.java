package com.escontrela.lastmove.domain.service;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.PositionPiece;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThreatenedSquaresServiceTest {

  private final ThreatenedSquaresService service = new ThreatenedSquaresService();

  @Test
  void attackedByReturnsEverySquareTheAttackerControls() {
    PositionSnapshot position = positionWithBlackQueenAttackingWhitePawn();

    Set<Square> attacked = service.attackedBy(position, PieceColor.BLACK);

    assertThat(attacked).contains(Square.of("e5"), Square.of("f5"), Square.of("g4"));
  }

  @Test
  void attackedPiecesOnlyReportsSquaresOccupiedByThreatenedPieces() {
    PositionSnapshot position = positionWithBlackQueenAttackingWhitePawn();

    Set<Square> threatened = service.attackedPieces(position, PieceColor.BLACK);

    assertThat(threatened).containsExactly(Square.of("e5"));
  }

  @Test
  void attackedPiecesExcludesEmptyAttackedSquaresAndUnattackedPieces() {
    PositionSnapshot position = positionWithBlackQueenAttackingWhitePawn();

    Set<Square> threatened = service.attackedPieces(position, PieceColor.BLACK);

    assertThat(threatened)
        .doesNotContain(Square.of("f5"), Square.of("g4"))
        .doesNotContain(Square.of("e1"));
  }

  private PositionSnapshot positionWithBlackQueenAttackingWhitePawn() {
    return new PositionSnapshot(
        List.of(
            new PositionPiece(Square.of("e5"), PieceType.PAWN, PieceColor.WHITE),
            new PositionPiece(Square.of("e1"), PieceType.KING, PieceColor.WHITE),
            new PositionPiece(Square.of("h5"), PieceType.QUEEN, PieceColor.BLACK),
            new PositionPiece(Square.of("h8"), PieceType.KING, PieceColor.BLACK)),
        PieceColor.WHITE,
        Optional.empty(),
        false,
        false);
  }
}