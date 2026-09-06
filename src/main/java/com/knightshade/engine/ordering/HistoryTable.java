package com.knightshade.engine.ordering;

import com.knightshade.engine.board.Move;
import com.knightshade.engine.board.Position;
import com.escontrela.lastmove.domain.common.PieceColor;

/** Bounded, side-specific butterfly history with gravity updates for rewards and penalties. */
public final class HistoryTable {
  private static final int LIMIT = 16_384;
  private final int[][][] scores = new int[2][64][64];

  public void record(Move move, int depth) {
    record(PieceColor.WHITE, move, depth);
  }

  public void record(PieceColor side, Move move, int depth) {
    update(side, move, bonus(depth));
  }

  public void penalize(PieceColor side, Move move, int depth) {
    update(side, move, -bonus(depth));
  }

  public int get(Move move) {
    return get(PieceColor.WHITE, move);
  }

  public int get(PieceColor side, Move move) {
    return scores[side.ordinal()][Position.indexOf(move.from())][Position.indexOf(move.to())];
  }

  private int bonus(int depth) {
    int boundedDepth = Math.max(0, Math.min(16, depth));
    return Math.min(2048, boundedDepth * boundedDepth * 32);
  }

  private void update(PieceColor side, Move move, int bonus) {
    if (move.isCapture() || move.isPromotion()) {
      return;
    }
    int from = Position.indexOf(move.from());
    int to = Position.indexOf(move.to());
    int current = scores[side.ordinal()][from][to];
    scores[side.ordinal()][from][to] = current + bonus - current * Math.abs(bonus) / LIMIT;
  }
}
