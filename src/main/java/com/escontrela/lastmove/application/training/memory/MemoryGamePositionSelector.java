package com.escontrela.lastmove.application.training.memory;

import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.game.PositionPiece;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.game.ChessRulesEngine;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.training.memory.MemoryGameDifficulty;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Selects valid, playable positions and distinct non-king pieces for memory-game rounds. */
@Component
public final class MemoryGamePositionSelector {
  private final MemoryGamePositionRepository repository;
  private final ChessRulesEngine rulesEngine;
  private final RandomGenerator random;
  private final Set<String> usedPositionKeys = new HashSet<>();

  public MemoryGamePositionSelector(
      MemoryGamePositionRepository repository, ChessRulesEngine rulesEngine, RandomGenerator random) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.rulesEngine = Objects.requireNonNull(rulesEngine, "rulesEngine must not be null");
    this.random = Objects.requireNonNull(random, "random must not be null");
  }

  @Autowired
  public MemoryGamePositionSelector(
      MemoryGamePositionRepository repository, ChessRulesEngine rulesEngine) {
    this(repository, rulesEngine, new SecureRandom());
  }

  /** Selects a new challenge, or empty when no eligible unused position exists. */
  public Optional<MemoryGameChallenge> next(MemoryGameDifficulty difficulty) {
    MemoryGameDifficulty required = Objects.requireNonNull(difficulty, "difficulty must not be null");
    List<ParsedPosition> eligible = repository.findAllPlayedPositions().stream()
        .map(this::parse)
        .flatMap(Optional::stream)
        .filter(candidate -> countHideable(candidate.snapshot()) >= required.hiddenPieceCount())
        .toList();
    if (eligible.isEmpty()) return Optional.empty();

    List<ParsedPosition> available = eligible.stream()
        .filter(candidate -> !usedPositionKeys.contains(candidate.fenKey()))
        .toList();
    if (available.isEmpty()) {
      usedPositionKeys.clear();
      available = eligible;
    }

    ParsedPosition selected = available.get(random.nextInt(available.size()));
    List<PositionPiece> hideable = selected.snapshot().pieces().stream()
        .filter(piece -> piece.type() != PieceType.KING)
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    java.util.Collections.shuffle(hideable, new java.util.Random(random.nextLong()));
    List<MemoryGamePiece> hidden = hideable.stream().limit(required.hiddenPieceCount())
        .map(piece -> new MemoryGamePiece(piece.square(), piece.type(), piece.color()))
        .toList();
    usedPositionKeys.add(selected.fenKey());
    return Optional.of(new MemoryGameChallenge(selected.position(), hidden));
  }

  /** Starts a new session selection pool. */
  public void reset() {
    usedPositionKeys.clear();
  }

  private Optional<ParsedPosition> parse(MemoryGamePosition source) {
    try {
      PositionSnapshot snapshot = rulesEngine.positionFrom(Fen.of(source.fen()));
      String normalized = source.fen().trim().replaceAll("\\s+", " ");
      return Optional.of(new ParsedPosition(new MemoryGamePosition(source.sourceId(), normalized), snapshot));
    } catch (RuntimeException invalidPosition) {
      return Optional.empty();
    }
  }

  private static int countHideable(PositionSnapshot snapshot) {
    return (int) snapshot.pieces().stream().filter(piece -> piece.type() != PieceType.KING).count();
  }

  private record ParsedPosition(MemoryGamePosition position, PositionSnapshot snapshot) {
    String fenKey() { return position.fen(); }
  }
}
