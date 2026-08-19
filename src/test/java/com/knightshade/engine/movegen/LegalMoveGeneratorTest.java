package com.knightshade.engine.movegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.FenParser;
import com.knightshade.engine.board.Move;
import com.knightshade.engine.board.MoveFlag;
import com.knightshade.engine.board.Piece;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class LegalMoveGeneratorTest {

  private static final String STARTING_FEN =
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

  private final LegalMoveGenerator moveGenerator = new LegalMoveGenerator();

  @Test
  void startingPositionHasTwentyMoves() {
    assertEquals(20, moveGenerator.generate(FenParser.parse(STARTING_FEN)).size());
  }

  @Test
  void generatesBothCastlingDirectionsWhenAvailable() {
    List<Move> moves = moveGenerator.generate(FenParser.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"));

    assertTrue(contains(moves, "e1g1", MoveFlag.KING_CASTLE));
    assertTrue(contains(moves, "e1c1", MoveFlag.QUEEN_CASTLE));
  }

  @Test
  void generatesTheEnPassantCapture() {
    String fen = "rnbqkbnr/ppp2ppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3";

    List<Move> moves = moveGenerator.generate(FenParser.parse(fen));

    assertTrue(contains(moves, "e5d6", MoveFlag.EN_PASSANT, PieceType.PAWN));
  }

  @Test
  void enPassantRemovesTheCapturedPawn() {
    String fen = "rnbqkbnr/ppp2ppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3";
    Board board = FenParser.parse(fen);

    Move enPassant = new Move(Square.of("e5"), Square.of("d6"), null, MoveFlag.EN_PASSANT, PieceType.PAWN);
    board.make(enPassant);

    assertEquals(Piece.NONE, board.pieceAt(Square.of("d5")));
    assertEquals(PieceType.PAWN, Piece.type(board.pieceAt(Square.of("d6"))));
  }

  @Test
  void generatesAllFourPromotionPieces() {
    String fen = "8/P7/8/8/8/8/8/k6K w - - 0 1";

    List<Move> moves = moveGenerator.generate(FenParser.parse(fen));

    for (PieceType promotion : List.of(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
      assertTrue(
          moves.stream()
              .anyMatch(
                  move ->
                      move.from().equals(Square.of("a7"))
                          && move.to().equals(Square.of("a8"))
                          && move.promotion() == promotion),
          "missing promotion to " + promotion);
    }
  }

  @Test
  void inCheckOnlyEscapingMovesAreLegal() {
    String fen = "8/8/8/8/8/3p4/4r3/4K3 w - - 0 1";

    Set<String> moves = uciSet(moveGenerator.generate(FenParser.parse(fen)));

    assertEquals(Set.of("e1d1", "e1f1"), moves);
  }

  private boolean contains(List<Move> moves, String uci, MoveFlag flag) {
    return contains(moves, uci, flag, null);
  }

  private boolean contains(List<Move> moves, String uci, MoveFlag flag, PieceType captured) {
    return moves.stream()
        .anyMatch(
            move ->
                move.toUci().equals(uci)
                    && move.flag() == flag
                    && move.captured() == captured);
  }

  private Set<String> uciSet(List<Move> moves) {
    return moves.stream().map(Move::toUci).collect(Collectors.toSet());
  }
}
