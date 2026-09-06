package com.knightshade.engine.ordering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knightshade.engine.board.FenParser;
import com.knightshade.engine.board.Move;
import com.knightshade.engine.movegen.LegalMoveGenerator;
import com.escontrela.lastmove.domain.common.PieceColor;
import java.util.List;
import org.junit.jupiter.api.Test;

class MoveOrderingTest {
  @Test
  void prioritizesHashThenKillerThenQuietOverALosingCaptureWithoutMutatingInput() {
    var board = FenParser.parse("6k1/2p5/3r4/4Q3/8/8/8/7K w - - 0 1");
    var moves = new LegalMoveGenerator().generate(board);
    Move losing = find(moves, "e5d6");
    Move quiet = find(moves, "e5e4");
    Move killer = find(moves, "e5f4");
    Move hash = find(moves, "e5g5");
    var input = List.of(losing, quiet, killer, hash);
    var killers = new KillerMoves();
    killers.record(killer, 0);
    var result = new MvvLvaMoveOrderer().order(board, input,
        new OrderingContext(0, killers, new HistoryTable(), hash));
    assertEquals(List.of(hash, killer, quiet, losing), result);
    assertEquals(List.of(losing, quiet, killer, hash), input);
  }

  @Test
  void historyIsBoundedLearnsFailuresAndKeepsColorsSeparate() {
    var board = FenParser.parse("6k1/8/8/4Q3/8/8/8/7K w - - 0 1");
    Move move = find(new LegalMoveGenerator().generate(board), "e5e4");
    var history = new HistoryTable();
    for (int i = 0; i < 100000; i++) {
      history.record(PieceColor.WHITE, move, 128);
    }
    assertTrue(history.get(PieceColor.WHITE, move) > 0);
    assertTrue(history.get(PieceColor.WHITE, move) <= 16384);
    assertEquals(0, history.get(PieceColor.BLACK, move));
    for (int i = 0; i < 1000; i++) {
      history.penalize(PieceColor.WHITE, move, 8);
    }
    assertTrue(history.get(PieceColor.WHITE, move) < 0);
    assertTrue(history.get(PieceColor.WHITE, move) >= -16384);
  }

  private Move find(List<Move> moves, String uci) {
    return moves.stream().filter(move -> move.toUci().equals(uci)).findFirst().orElseThrow();
  }
}
