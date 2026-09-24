package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerEngineIds;
import com.escontrela.lastmove.application.computer.ComputerMoveEngine;
import com.escontrela.lastmove.application.computer.ComputerMoveEngineProvider;
import com.escontrela.lastmove.application.computer.ComputerMoveRequest;
import com.escontrela.lastmove.application.computer.PonderResourceCoordinator;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Application use case for evaluating the strength of one position and its best continuation.
 *
 * <p>The service reuses the same engine providers as the progressive game but keeps a single
 * engine instance alive for the lifetime of the analysis workflow, so external engines are not
 * restarted on every navigation step. A request waiting for CPU admission is superseded by a newer
 * position, while an admitted search is allowed to finish and publish its result.
 */
@Service
public final class PositionAnalysisService {

  private final Map<String, ComputerMoveEngineProvider> providers;
  private final ChessRulesEngine rulesEngine;
  private final ComputerEngineSettingsService settingsService;
  private final AtomicLong version = new AtomicLong();
  private final AtomicLong cancellationVersion = new AtomicLong();
  private volatile PonderResourceCoordinator.AnalysisAdmission pendingAdmission;
  private volatile CompletableFuture<Optional<PositionAnalysisResult>> pendingAnalysis;

  private ComputerMoveEngine currentEngine;
  private String currentEngineId;
  private boolean currentKnightshadeBitboardsEnabled;

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
   * <p>The returned stage completes empty when it was superseded before admission or explicitly
   * cancelled. Once admitted, the result is retained even if the game advances to another position.
   */
  public CompletionStage<Optional<PositionAnalysisResult>> analyze(
      PositionSnapshot position, String engineId) {
    String requiredId = Objects.requireNonNull(engineId, "engineId must not be null").trim();
    return analyze(position, requiredId, settingsService.thinkingTime(requiredId));
  }

  /** Analyses one position with a caller-selected budget, used by compact live indicators. */
  public CompletionStage<Optional<PositionAnalysisResult>> analyze(
      PositionSnapshot position, String engineId, Duration maximumThinkingTime) {
    PositionSnapshot requiredPosition =
        Objects.requireNonNull(position, "position must not be null");
    String requiredId = Objects.requireNonNull(engineId, "engineId must not be null").trim();
    Duration requiredThinkingTime =
        Objects.requireNonNull(maximumThinkingTime, "maximumThinkingTime must not be null");
    if (requiredThinkingTime.isZero() || requiredThinkingTime.isNegative()) {
      throw new IllegalArgumentException("maximumThinkingTime must be positive");
    }

    final ComputerMoveEngine engine;
    final ComputerEngineDescriptor descriptor;
    try {
      descriptor = descriptor(requiredId);
      engine = engineFor(requiredId);
    } catch (RuntimeException exception) {
      return CompletableFuture.failedFuture(exception);
    }

    final long requestedVersion = version.incrementAndGet();
    final long requestedCancellationVersion = cancellationVersion.get();
    cancelPendingAdmission();
    return engine
        .start()
        .thenCompose(
            ignored -> analyzeWhenAdmitted(
                engine, descriptor, requiredPosition, requiredThinkingTime,
                requestedVersion, requestedCancellationVersion));
  }

  /** Cancels both an admitted search and any retry waiting for CPU capacity. */
  public void cancel() {
    cancellationVersion.incrementAndGet();
    version.incrementAndGet();
    cancelPendingAdmission();
    ComputerMoveEngine engine = currentEngine;
    if (engine != null) engine.cancelSearch();
  }

  private void cancelPendingAdmission() {
    PonderResourceCoordinator.AnalysisAdmission admission = pendingAdmission;
    pendingAdmission = null;
    CompletableFuture<Optional<PositionAnalysisResult>> pending = pendingAnalysis;
    pendingAnalysis = null;
    if (admission != null && admission.cancel() && pending != null) {
      pending.complete(Optional.empty());
    }
  }

  private CompletionStage<Optional<PositionAnalysisResult>> analyzeWhenAdmitted(
      ComputerMoveEngine engine,
      ComputerEngineDescriptor descriptor,
      PositionSnapshot position,
      Duration thinkingTime,
      long requestedVersion,
      long requestedCancellationVersion) {
    if (requestedVersion != version.get()
        || requestedCancellationVersion != cancellationVersion.get()) {
      return CompletableFuture.completedFuture(Optional.empty());
    }
    CompletableFuture<Optional<PositionAnalysisResult>> deferred = new CompletableFuture<>();
    pendingAnalysis = deferred;
    PonderResourceCoordinator.AnalysisAdmission admission =
        PonderResourceCoordinator.awaitAnalysis(resourceLease -> {
          if (requestedVersion != version.get()
              || requestedCancellationVersion != cancellationVersion.get()) {
            resourceLease.close();
            deferred.complete(Optional.empty());
            return;
          }
          try {
            engine.analyze(new ComputerMoveRequest(position, thinkingTime))
                .whenComplete((result, failure) -> {
                  resourceLease.close();
                  if (requestedCancellationVersion != cancellationVersion.get()) {
                    deferred.complete(Optional.empty());
                  }
                  else if (failure != null) deferred.completeExceptionally(failure);
                  else deferred.complete(Optional.of(toResult(descriptor, position, result)));
                });
          } catch (RuntimeException failure) {
            resourceLease.close();
            deferred.completeExceptionally(new CompletionException(failure));
          }
        });
    pendingAdmission = admission;
    return deferred;
  }

  /** Closes the retained engine instance, if any. */
  @PreDestroy
  public void close() {
    cancel();
    closeCurrentEngine();
  }

  private ComputerMoveEngine engineFor(String engineId) {
    boolean knightshade = ComputerEngineIds.KNIGHTSHADE.equals(engineId);
    boolean bitboardsEnabled = knightshade && settingsService.knightshadeBitboardsEnabled();
    if (currentEngine != null && engineId.equals(currentEngineId)
        && (!knightshade || currentKnightshadeBitboardsEnabled == bitboardsEnabled)) {
      return currentEngine;
    }
    closeCurrentEngine();
    currentEngine = providers.get(engineId).create();
    currentEngineId = engineId;
    currentKnightshadeBitboardsEnabled = bitboardsEnabled;
    return currentEngine;
  }

  private void closeCurrentEngine() {
    if (currentEngine != null) {
      currentEngine.close();
      currentEngine = null;
      currentEngineId = null;
      currentKnightshadeBitboardsEnabled = false;
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
        result.depth(), result.nodes());
  }

  private Optional<String> toSan(PositionSnapshot position, MoveCommand move) {
    MoveExecutionResult executed = rulesEngine.execute(position, move);
    if (!executed.accepted()) {
      return Optional.empty();
    }
    return executed.move().map(descriptor -> descriptor.san().getValue());
  }
}
