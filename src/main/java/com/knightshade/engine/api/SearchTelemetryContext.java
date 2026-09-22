package com.knightshade.engine.api;

import com.escontrela.lastmove.domain.common.PieceColor;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable, reproducible identity and input of one engine search. */
public record SearchTelemetryContext(
    String gameId,
    String searchId,
    String rootFen,
    PieceColor sideToMove,
    int fullmoveNumber,
    Instant startedAt,
    String limits,
    String engineVersion,
    String engineBuild,
    List<String> positionHistory) {

  public SearchTelemetryContext {
    gameId = requireText(gameId, "gameId");
    searchId = requireText(searchId, "searchId");
    rootFen = requireText(rootFen, "rootFen");
    sideToMove = Objects.requireNonNull(sideToMove, "sideToMove must not be null");
    if (fullmoveNumber < 1) throw new IllegalArgumentException("fullmoveNumber must be positive");
    startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
    limits = requireText(limits, "limits");
    engineVersion = requireText(engineVersion, "engineVersion");
    engineBuild = requireText(engineBuild, "engineBuild");
    positionHistory = List.copyOf(Objects.requireNonNull(positionHistory, "positionHistory must not be null"));
  }

  public static SearchTelemetryContext unscoped(
      String fen, PieceColor side, int fullmove, String limits, List<String> history) {
    return new SearchTelemetryContext(
        "unscoped", UUID.randomUUID().toString(), fen, side, fullmove, Instant.now(), limits,
        implementationVersion(), System.getProperty("knightshade.build", "development"), history);
  }

  public static SearchTelemetryContext forSearch(
      String gameId, String fen, SearchLimits limits, List<String> history) {
    String[] fields = fen.trim().split("\\s+");
    if (fields.length < 6) throw new IllegalArgumentException("FEN must contain six fields");
    PieceColor side = "w".equals(fields[1]) ? PieceColor.WHITE : PieceColor.BLACK;
    int fullmove = Integer.parseInt(fields[5]);
    return new SearchTelemetryContext(gameId, UUID.randomUUID().toString(), fen, side, fullmove,
        Instant.now(), limits.toString(), implementationVersion(),
        System.getProperty("knightshade.build", "development"), history);
  }

  private static String implementationVersion() {
    String version = SearchTelemetryContext.class.getPackage().getImplementationVersion();
    return version == null || version.isBlank() ? "development" : version;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    return value;
  }
}
