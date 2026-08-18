package com.knightshade.engine.evaluation.term;

import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.knightshade.engine.evaluation.PositionalTerm;
import com.escontrela.lastmove.domain.common.PieceType;

/** Rewards knights, bishops, rooks and queens for the number of squares they attack. */
public final class MobilityTerm implements PositionalTerm {

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
    return mobility(position, true) - mobility(position, false);
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
}
