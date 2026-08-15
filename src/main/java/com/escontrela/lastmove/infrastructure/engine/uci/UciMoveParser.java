package com.escontrela.lastmove.infrastructure.engine.uci;

import com.escontrela.lastmove.application.computer.ComputerEngineException;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.MoveCommand;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts UCI coordinate moves such as {@code e2e4} and {@code e7e8q} to domain commands. */
public final class UciMoveParser {

  private static final Pattern MOVE_PATTERN =
      Pattern.compile("^([a-h][1-8])([a-h][1-8])([qrbn])?$");

  private UciMoveParser() {}

  /** Parses one UCI move token, rejecting null moves and malformed engine output. */
  public static MoveCommand parse(String value) {
    String required =
        value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    Matcher matcher = MOVE_PATTERN.matcher(required);
    if (!matcher.matches()) {
      throw new ComputerEngineException("The engine returned an invalid UCI move: " + value);
    }
    return new MoveCommand(
        Square.of(matcher.group(1)),
        Square.of(matcher.group(2)),
        Optional.ofNullable(matcher.group(3)).map(UciMoveParser::promotion));
  }

  private static PieceType promotion(String symbol) {
    return switch (symbol) {
      case "q" -> PieceType.QUEEN;
      case "r" -> PieceType.ROOK;
      case "b" -> PieceType.BISHOP;
      case "n" -> PieceType.KNIGHT;
      default -> throw new ComputerEngineException("Unsupported UCI promotion: " + symbol);
    };
  }
}
