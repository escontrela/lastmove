package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerEngineIds;
import com.escontrela.lastmove.application.computer.ComputerMoveEngine;
import com.escontrela.lastmove.application.computer.ComputerMoveEngineProvider;
import com.escontrela.lastmove.application.computer.ComputerMoveRequest;
import com.escontrela.lastmove.application.computer.EngineAnalysisResult;
import com.escontrela.lastmove.application.computer.EngineScoreFormatter;
import com.escontrela.lastmove.application.dto.PositionAnalysisResult;
import com.escontrela.lastmove.domain.game.ChessRulesEngine;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Application use case for evaluating the strength of one position and its best continuation.
 *
 * <p>The service reuses the same engine providers as the progressive game but keeps a single
 * engine instance alive for the lifetime of the analysis workflow, so external engines are not
 * restarted on every navigation step. Results are guarded against out-of-order completion: a stale
 * search that is superseded by a newer request completes with an empty result.
 */
@Service
public final class PositionAnalysisService {

  private final Map<String, ComputerMoveEngineProvider> providers;
  private final ChessRulesEngine rulesEngine;
  private final ComputerEngineSettingsService settingsService;

  private ComputerMoveEngine currentEngine;
  private String currentEngineId;
  private volatile long version;

  public PositionAnalysisService(
      List<ComputerMoveEngineProvider> providers,
      ChessRulesEngine rulesEngine,
      ComputerEngineSettingsService settingsService) {
    Objects.requireNonNull(providers, "providers must not be null");
    this.providers =
        providers.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    provider -> provider.descriptor().id(), provider -> provider));
    this.rulesEngine = Objects.requireNonNull(rulesEngine, "rulesEngine must not be null");
    this.settingsService =
        Objects.requireNonNull(settingsService, "settingsService must not be null");
  }

  /** Lists the engines selectable for analysis in the same stable order as the game setup. */
  public List<ComputerEngineDescriptor> availableEngines() {
    return providers.values().stream()
        .map(ComputerMoveEngineProvider::descriptor)
        .sorted(
            java.util.Comparator.comparing(ComputerEngineDescriptor::displayName)
                .thenComparing(ComputerEngineDescriptor::id))
        .toList();
  }

  /** Returns the engine used by default for analysis, falling back to Knightshade. */
  public String defaultEngineId() {
    return settingsService
        .defaultAnalysisEngineId()
        .filter(providers::containsKey)
        .orElseGet(
            () ->
                providers.containsKey(ComputerEngineIds.KNIGHTSHADE)
                    ? ComputerEngineIds.KNIGHTSHADE
                    : availableEngines().getFirst().id());
  }

  /**
   * Analyses one position with the selected engine.
   *
   * <p>The returned stage completes with an empty optional when this request was superseded by a
   * newer one before the engine replied, allowing callers to discard stale results without any
   * position comparison.
   */
  public CompletionStage<Optional<PositionAnalysisResult>> analyze(
      PositionSnapshot position, String engineId) {
    PositionSnapshot requiredPosition =
        Objects.requireNonNull(position, "position must not be null");
    String requiredId = Objects.requireNonNull(engineId, "engineId must not be null").trim();

    final ComputerMoveEngine engine;
    final ComputerEngineDescriptor descriptor;
    try {
      descriptor = descriptor(requiredId);
      engine = engineFor(requiredId);
    } catch (RuntimeException exception) {
      return CompletableFuture.failedFuture(exception);
    }

    final long requestedVersion = ++version;
    Duration thinkingTime = settingsService.thinkingTime(requiredId);
    return engine
        .start()
        .thenCompose(
            ignored ->
                engine.analyze(new ComputerMoveRequest(requiredPosition, thinkingTime)))
        .thenApply(
            result ->
                requestedVersion == version
                    ? Optional.of(toResult(descriptor, requiredPosition, result))
                    : Optional.empty());
  }

  /** Closes the retained engine instance, if any. */
  @PreDestroy
  public void close() {
    closeCurrentEngine();
  }

  private ComputerMoveEngine engineFor(String engineId) {
    if (currentEngine != null && engineId.equals(currentEngineId)) {
      return currentEngine;
    }
    closeCurrentEngine();
    currentEngine = providers.get(engineId).create();
    currentEngineId = engineId;
    return currentEngine;
  }

  private void closeCurrentEngine() {
    if (currentEngine != null) {
      currentEngine.close();
      currentEngine = null;
      currentEngineId = null;
    }
  }

  private ComputerEngineDescriptor descriptor(String engineId) {
    ComputerMoveEngineProvider provider = providers.get(engineId);
    if (provider == null) {
      throw new NoSuchElementException("Unknown computer engine: " + engineId);
    }
    return provider.descriptor();
  }

  private PositionAnalysisResult toResult(
      ComputerEngineDescriptor descriptor,
      PositionSnapshot position,
      EngineAnalysisResult result) {
    Optional<String> bestMoveSan =
        result.bestMove().flatMap(move -> toSan(position, move));
    Optional<String> scoreText =
        result.score().map(score -> EngineScoreFormatter.format(score, position.activeColor()));
    return new PositionAnalysisResult(
        descriptor.displayName() + " " + descriptor.version(),
        bestMoveSan,
        scoreText,
        result.depth());
  }

  private Optional<String> toSan(PositionSnapshot position, MoveCommand move) {
    MoveExecutionResult executed = rulesEngine.execute(position, move);
    if (!executed.accepted()) {
      return Optional.empty();
    }
    return executed.move().map(descriptor -> descriptor.san().getValue());
  }
}
