package com.knightshade.engine.evaluation;

import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;

/**
 * v3 evaluator: material, piece-square tables, mobility and king safety, White minus Black.
 *
 * <p>Mobility rewards pieces for the number of squares they attack; king safety rewards a pawn
 * shield in front of the king. Both are computed from the read-only {@link Position} view, so the
 * evaluator remains representation-agnostic.
 */
public final class PositionalEvaluator implements Evaluator {

  private static final int[][] KNIGHT_OFFSETS = {
    {1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}
  };

  private static final int[][] ORTHOGONAL = {
    {1, 0}, {-1, 0}, {0, 1}, {0, -1}
  };

  private static final int[][] DIAGONAL = {
    {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
  };

  private static final int[][] ALL_DIRECTIONS = {
    {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
  };

  @Override
  public int evaluate(Position position) {
    int score = 0;
    for (int index = 0; index < 64; index++) {
      int piece = position.pieceAt(index);
      if (piece == Piece.NONE) {
        continue;
      }
      boolean white = Piece.isWhite(piece);
      int value =
          PieceValues.of(Piece.type(piece)) + PieceSquareTables.value(Piece.type(piece), index, white);
      score += white ? value : -value;
    }
    score += mobility(position, true) - mobility(position, false);
    score += kingShield(position, PieceColor.WHITE) - kingShield(position, PieceColor.BLACK);
    return score;
  }

  private int mobility(Position position, boolean white) {
    int score = 0;
    for (int index = 0; index < 64; index++) {
      int piece = position.pieceAt(index);
      if (piece == Piece.NONE || Piece.isWhite(piece) != white) {
        continue;
      }
      int count = attackCount(position, index);
      score +=
          switch (Piece.type(piece)) {
            case KNIGHT -> (count - 4) * 4;
            case BISHOP -> (count - 6) * 3;
            case ROOK -> (count - 7) * 2;
            case QUEEN -> (count - 14);
            default -> 0;
          };
    }
    return score;
  }

  private int attackCount(Position position, int fromIndex) {
    int piece = position.pieceAt(fromIndex);
    int file = fromIndex & 7;
    int rank = fromIndex >>> 3;
    boolean white = Piece.isWhite(piece);
    int count = 0;

    switch (Piece.type(piece)) {
      case KNIGHT -> {
        for (int[] offset : KNIGHT_OFFSETS) {
          if (attackable(position, file + offset[0], rank + offset[1], white)) {
            count++;
          }
        }
      }
      case BISHOP -> {
        for (int[] direction : DIAGONAL) {
          count += rayCount(position, file, rank, direction, white);
        }
      }
      case ROOK -> {
        for (int[] direction : ORTHOGONAL) {
          count += rayCount(position, file, rank, direction, white);
        }
      }
      case QUEEN -> {
        for (int[] direction : ALL_DIRECTIONS) {
          count += rayCount(position, file, rank, direction, white);
        }
      }
      default -> {}
    }
    return count;
  }

  private int rayCount(Position position, int file, int rank, int[] direction, boolean white) {
    int count = 0;
    int nextFile = file + direction[0];
    int nextRank = rank + direction[1];
    while (nextFile >= 0 && nextFile < 8 && nextRank >= 0 && nextRank < 8) {
      int target = position.pieceAt(nextRank * 8 + nextFile);
      if (target == Piece.NONE) {
        count++;
      } else {
        if (Piece.isWhite(target) != white) {
          count++;
        }
        break;
      }
      nextFile += direction[0];
      nextRank += direction[1];
    }
    return count;
  }

  private boolean attackable(Position position, int file, int rank, boolean white) {
    if (file < 0 || file > 7 || rank < 0 || rank > 7) {
      return false;
    }
    int target = position.pieceAt(rank * 8 + file);
    return target == Piece.NONE || Piece.isWhite(target) != white;
  }

  private int kingShield(Position position, PieceColor color) {
    int kingIndex = -1;
    for (int index = 0; index < 64; index++) {
      if (Piece.is(position.pieceAt(index), color, PieceType.KING)) {
        kingIndex = index;
        break;
      }
    }
    if (kingIndex == -1) {
      return 0;
    }
    int file = kingIndex & 7;
    int rank = kingIndex >>> 3;
    int direction = color == PieceColor.WHITE ? 1 : -1;
    int score = 0;
    for (int fileDelta = -1; fileDelta <= 1; fileDelta++) {
      for (int distance = 1; distance <= 2; distance++) {
        int nextFile = file + fileDelta;
        int nextRank = rank + direction * distance;
        if (nextFile < 0 || nextFile > 7 || nextRank < 0 || nextRank > 7) {
          continue;
        }
        if (Piece.is(position.pieceAt(nextRank * 8 + nextFile), color, PieceType.PAWN)) {
          score += distance == 1 ? 10 : 5;
        }
      }
    }
    return score;
  }
}
