package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.computer.ComputerGameConfiguration;
import com.escontrela.lastmove.application.computer.OpeningPracticeConfiguration;
import com.escontrela.lastmove.domain.game.ChessGame;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.notation.SanMove;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Builds a validated opening-practice configuration from the SAN line entered by a player. */
@Service
public final class OpeningPracticeService {

  public static final int DEFAULT_SAFETY_THRESHOLD_CENTIPAWNS = 75;

  private final ChessGameFactory gameFactory;

  public OpeningPracticeService(ChessGameFactory gameFactory) {
    this.gameFactory = Objects.requireNonNull(gameFactory, "gameFactory must not be null");
  }

  /**
   * Applies an optional SAN opening line to a game setup.
   *
   * <p>Blank lines leave opening practice disabled. A supplied line is resolved one move at a
   * time against the selected starting position, so ambiguous or illegal SAN never reaches the
   * computer-game runtime.
   */
  public ComputerGameConfiguration configure(
      ComputerGameConfiguration configuration, String sanLine, String thresholdCentipawns) {
    ComputerGameConfiguration required =
        Objects.requireNonNull(configuration, "configuration must not be null");
    String line = Objects.requireNonNullElse(sanLine, "").trim();
    if (line.isEmpty()) {
      return required;
    }
    int threshold = parseThreshold(thresholdCentipawns);
    ChessGame game =
        required.startingFen().map(gameFactory::createAnalysisGame).orElseGet(gameFactory::createAnalysisGame);
    List<MoveCommand> moves = new ArrayList<>();
    for (String token : sanTokens(line)) {
      try {
        var result = game.move(SanMove.of(token));
        if (!result.accepted()) {
          throw new IllegalArgumentException(
              "Illegal opening move '" + token + "': " + result.rejectionReason().orElse("not legal here"));
        }
        var move = game.moveHistory().getLast().move();
        moves.add(new MoveCommand(move.from(), move.to(), move.promotion()));
      } catch (IllegalArgumentException exception) {
        throw new IllegalArgumentException("Invalid opening sequence at '" + token + "': " + exception.getMessage(), exception);
      }
    }
    if (moves.isEmpty()) {
      throw new IllegalArgumentException("Enter at least one SAN move for the opening practice line.");
    }
    return new ComputerGameConfiguration(
        required.humanName(),
        required.humanColor(),
        required.timeControl(),
        required.startingFen(),
        required.engineId(),
        required.engineThinkingTime(),
        java.util.Optional.of(new OpeningPracticeConfiguration(moves, threshold)));
  }

  private static int parseThreshold(String rawValue) {
    String value = Objects.requireNonNullElse(rawValue, "").trim();
    if (value.isEmpty()) {
      return DEFAULT_SAFETY_THRESHOLD_CENTIPAWNS;
    }
    try {
      int threshold = Integer.parseInt(value);
      if (threshold < 0) {
        throw new IllegalArgumentException("Safety threshold must be zero or greater.");
      }
      return threshold;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Safety threshold must be a whole number of centipawns.", exception);
    }
  }

  private static List<String> sanTokens(String line) {
    String withoutMoveNumbers = line.replaceAll("(?<!\\S)\\d+\\.(?:\\.\\.)?", " ");
    return java.util.Arrays.stream(withoutMoveNumbers.split("\\s+"))
        .filter(token -> !token.isBlank())
        .filter(token -> !token.matches("1-0|0-1|1/2-1/2|\\*"))
        .toList();
  }
}
