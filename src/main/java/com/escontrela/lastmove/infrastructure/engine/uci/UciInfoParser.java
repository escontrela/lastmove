package com.escontrela.lastmove.infrastructure.engine.uci;

import com.escontrela.lastmove.application.computer.EngineScore;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts evaluation information from one UCI {@code info} line. */
public final class UciInfoParser {

  private static final Pattern CENTIPAWN_SCORE = Pattern.compile("\\bscore cp (-?\\d+)\\b");
  private static final Pattern MATE_SCORE = Pattern.compile("\\bscore mate (-?\\d+)\\b");
  private static final Pattern DEPTH = Pattern.compile("\\bdepth (\\d+)\\b");

  private UciInfoParser() {}

  /**
   * Parses the last evaluation carried by an {@code info} line, preferring a centipawn score and
   * falling back to a mate score.
   */
  public static Optional<EngineScore> parseScore(String line) {
    Matcher centipawns = CENTIPAWN_SCORE.matcher(line);
    if (centipawns.find()) {
      return Optional.of(EngineScore.centipawns(parseInt(centipawns.group(1))));
    }
    Matcher mate = MATE_SCORE.matcher(line);
    if (mate.find()) {
      return Optional.of(EngineScore.mateIn(parseInt(mate.group(1))));
    }
    return Optional.empty();
  }

  /** Parses the search depth reported by an {@code info} line, if present. */
  public static Optional<Integer> parseDepth(String line) {
    Matcher matcher = DEPTH.matcher(line);
    if (matcher.find()) {
      return Optional.of(parseInt(matcher.group(1)));
    }
    return Optional.empty();
  }

  private static int parseInt(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      return 0;
    }
  }
}
