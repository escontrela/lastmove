package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.computer.ComputerEngineHealth;
import com.escontrela.lastmove.application.computer.ComputerEngineHealthCheck;
import com.escontrela.lastmove.application.computer.ComputerEngineIds;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.springframework.stereotype.Service;

/** Resolves health checks by stable engine identity for Settings and game setup workflows. */
@Service
public final class ComputerEngineHealthService {

  private final List<ComputerEngineHealthCheck> healthChecks;

  public ComputerEngineHealthService(List<ComputerEngineHealthCheck> healthChecks) {
    this.healthChecks =
        List.copyOf(Objects.requireNonNull(healthChecks, "healthChecks must not be null"));
  }

  public CompletionStage<ComputerEngineHealth> checkSunfish() {
    return check(ComputerEngineIds.SUNFISH);
  }

  public CompletionStage<ComputerEngineHealth> check(String engineId) {
    String required = Objects.requireNonNull(engineId, "engineId must not be null");
    return healthChecks.stream()
        .filter(check -> check.descriptor().id().equals(required))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("Unknown computer engine: " + required))
        .check();
  }
}
