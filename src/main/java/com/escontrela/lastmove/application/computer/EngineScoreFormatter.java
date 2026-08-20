package com.escontrela.lastmove.application.computer;

import com.escontrela.lastmove.domain.common.PieceColor;
import java.util.Locale;
import java.util.Objects;

/**
 * Renders an {@link EngineScore} as human-readable text from White's perspective.
 *
 * <p>Engines report scores from the side to move; this formatter flips the sign when Black is to
 * move so that a positive value always favours White, matching the convention used by most chess
 * interfaces.
 */
public final class EngineScoreFormatter {

  private EngineScoreFormatter() {}

  /** Returns a White-perspective display such as {@code +0.35}, {@code -1.20} or {@code #3}. */
  public static String format(EngineScore score, PieceColor activeColor) {
    EngineScore required = Objects.requireNonNull(score, "score must not be null");
    PieceColor color = Objects.requireNonNull(activeColor, "activeColor must not be null");
    if (required.isMate()) {
      return formatMate(required.value(), color);
    }
    return formatCentipawns(required.value(), color);
  }

  private static String formatCentipawns(int sideToMoveValue, PieceColor activeColor) {
    int whiteValue = activeColor == PieceColor.WHITE ? sideToMoveValue : -sideToMoveValue;
    double pawns = whiteValue / 100.0;
    return String.format(Locale.ROOT, "%+.2f", pawns);
  }

  private static String formatMate(int sideToMovePlies, PieceColor activeColor) {
    int sign = sideToMovePlies > 0 ? 1 : -1;
    int whiteSign = activeColor == PieceColor.WHITE ? sign : -sign;
    int moves = (Math.abs(sideToMovePlies) + 1) / 2;
    return whiteSign > 0 ? "#" + moves : "-#" + moves;
  }
}
