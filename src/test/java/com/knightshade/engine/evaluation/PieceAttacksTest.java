package com.knightshade.engine.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.knightshade.engine.board.FenParser;
import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.knightshade.engine.movegen.LegalMoveGenerator;
import com.knightshade.engine.evaluation.term.KingSafetyTerm;
import com.knightshade.engine.evaluation.term.MobilityTerm;
import com.knightshade.engine.evaluation.term.RookActivityTerm;
import com.escontrela.lastmove.domain.common.PieceColor;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PieceAttacksTest {
  @Test
  void influenceMatchesIndependentBoardAttackDetectionAcrossLegalGames() {
    var random = new Random(991);
    var generator = new LegalMoveGenerator();
    for (String fen : List.of(
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        "r3k2r/8/8/3pP3/8/8/8/R3K2R w KQkq d6 0 1",
        "7k/P7/8/8/8/8/7p/K7 w - - 0 1")) {
      var board = FenParser.parse(fen);
      for (int ply = 0; ply < 150; ply++) {
        String[] ranks = board.toFen().split(" ")[0].split("/");
        var mirrored = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
          for (char c : ranks[rank].toCharArray()) {
            mirrored.append(Character.isUpperCase(c) ? Character.toLowerCase(c)
                : Character.toUpperCase(c));
          }
          if (rank > 0) mirrored.append('/');
        }
        var flipped = FenParser.parse(mirrored + " w - - 0 1");
        for (PositionalTerm term : List.of(new MobilityTerm(), new KingSafetyTerm(),
            new RookActivityTerm())) {
          assertEquals(-term.evaluate(board), term.evaluate(flipped), term.getClass().getSimpleName());
        }
        for (var color : PieceColor.values()) {
          long attacks = 0;
          for (int square = 0; square < 64; square++) {
            int piece = board.pieceAt(square);
            if (piece != Piece.NONE && Piece.color(piece) == color) {
              attacks |= PieceAttacks.from(board, square);
            }
          }
          for (int square = 0; square < 64; square++) {
            assertEquals(board.isSquareAttacked(Position.squareOf(square), color),
                (attacks & (1L << square)) != 0, board.toFen() + " square " + square);
          }
        }
        var moves = generator.generate(board);
        if (moves.isEmpty()) break;
        board.make(moves.get(random.nextInt(moves.size())));
      }
    }
  }
}
