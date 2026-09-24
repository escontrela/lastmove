package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.dto.PositionAnalysisResult;
import com.escontrela.lastmove.application.dto.EngineEvaluationState;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/** Coordinates the selected analysis engine and a single current evaluation request. */
@Service
public final class EngineEvaluationService {

  private final PositionAnalysisService positionAnalysisService;
  private final ComputerEngineSettingsService settingsService;
  private final CopyOnWriteArrayList<Consumer<EngineEvaluationState>> listeners =
      new CopyOnWriteArrayList<>();
  private final AtomicLong requestVersion = new AtomicLong();
  private volatile ComputerEngineDescriptor selectedEngine;
  private volatile EngineEvaluationState state;

  public EngineEvaluationService(
      PositionAnalysisService positionAnalysisService, ComputerEngineSettingsService settingsService) {
    this.positionAnalysisService = Objects.requireNonNull(positionAnalysisService, "positionAnalysisService");
    this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
    this.selectedEngine = descriptor(positionAnalysisService.defaultEngineId());
    this.state = EngineEvaluationState.idle(selectedEngine);
  }

  public List<ComputerEngineDescriptor> availableEngines() {
    return positionAnalysisService.availableEngines();
  }

  public EngineEvaluationState state() {
    return state;
  }

  public Runnable subscribe(Consumer<EngineEvaluationState> listener) {
    Consumer<EngineEvaluationState> required = Objects.requireNonNull(listener, "listener");
    listeners.add(required);
    required.accept(state);
    return () -> listeners.remove(required);
  }

  public void selectEngine(String engineId) {
    selectedEngine = descriptor(engineId);
    settingsService.updateDefaultAnalysisEngineId(Optional.of(selectedEngine.id()));
    requestVersion.incrementAndGet();
    positionAnalysisService.cancel();
    publish(EngineEvaluationState.idle(selectedEngine));
  }

  public void analyze(PositionSnapshot position) {
    analyze(position, null);
  }

  /** Evaluates with a bounded budget for compact, rapidly refreshed indicators. */
  public void analyze(PositionSnapshot position, Duration maximumThinkingTime) {
    long version = requestVersion.get();
    ComputerEngineDescriptor engine = selectedEngine;
    publish(EngineEvaluationState.searching(engine));
    CompletionStage<Optional<PositionAnalysisResult>> analysis =
        maximumThinkingTime == null
            ? positionAnalysisService.analyze(Objects.requireNonNull(position, "position"), engine.id())
            : positionAnalysisService.analyze(
                Objects.requireNonNull(position, "position"), engine.id(), maximumThinkingTime);
    analysis
        .whenComplete(
            (result, failure) -> {
              if (version != requestVersion.get()) {
                return;
              }
              if (failure != null) {
                publish(EngineEvaluationState.idle(engine));
                return;
              }
              if (result == null || result.isEmpty()) return;
              var analyzedPosition = result.orElseThrow();
              publish(
                  new EngineEvaluationState(
                      engine,
                      analyzedPosition.scoreText(),
                      analyzedPosition.depth(),
                      analyzedPosition.bestMoveSan(),
                      analyzedPosition.nodes(),
                      false));
            });
  }

  public void cancel() {
    requestVersion.incrementAndGet();
    positionAnalysisService.cancel();
    publish(EngineEvaluationState.idle(selectedEngine));
  }

  private ComputerEngineDescriptor descriptor(String engineId) {
    return availableEngines().stream()
        .filter(engine -> engine.id().equals(engineId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown analysis engine: " + engineId));
  }

  private void publish(EngineEvaluationState next) {
    state = next;
    listeners.forEach(listener -> listener.accept(next));
  }
}
