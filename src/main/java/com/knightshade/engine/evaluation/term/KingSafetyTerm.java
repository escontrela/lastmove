package com.knightshade.engine.evaluation.term;

import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.knightshade.engine.evaluation.PositionalTerm;
import com.knightshade.engine.evaluation.EvaluationAttacks;
import com.knightshade.engine.evaluation.GamePhase;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;

/** Pawn shelter and coordinated enemy pressure on the king, faded in endgames. */
public final class KingSafetyTerm implements PositionalTerm {

  @Override
  public int evaluate(Position position) {
    var attacks = new EvaluationAttacks();
    attacks.update(position);
    return evaluate(position, attacks);
  }

  public int evaluate(Position position, EvaluationAttacks attacks) {
    return (safety(position, PieceColor.WHITE, attacks) - safety(position, PieceColor.BLACK, attacks))
        * GamePhase.of(position) / GamePhase.MAX;
  }

  private int safety(Position position, PieceColor color, EvaluationAttacks attacks) {
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
    long zone = attacks.from(kingIndex) | (1L << kingIndex);
    int attackers = 0;
    int units = 0;
    boolean enemyQueen = false;
    for (int square = 0; square < 64; square++) {
      int piece = position.pieceAt(square);
      if (piece == Piece.NONE || Piece.color(piece) == color) continue;
      PieceType type = Piece.type(piece);
      if (type == PieceType.QUEEN) enemyQueen = true;
      int weight = switch (type) {
        case KNIGHT, BISHOP -> 2;
        case ROOK -> 3;
        case QUEEN -> 5;
        default -> 0;
      };
      if (weight == 0) continue;
      int contacts = Long.bitCount(attacks.from(square) & zone);
      if (contacts > 0) {
        attackers++;
        units += weight * Math.min(contacts, 3);
      }
    }
    // Multiple cooperating pieces are more dangerous than one unsupported attacker.
    int danger = Math.min(300, units * units / 4) * Math.min(attackers, 3) / 3;
    if (!enemyQueen) danger /= 2;
    return score - danger;
  }
}
