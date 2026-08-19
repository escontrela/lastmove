package com.escontrela.lastmove.infrastructure.engine.maia;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerMoveEngine;
import com.escontrela.lastmove.application.computer.ComputerMoveEngineProvider;
import com.escontrela.lastmove.application.service.ComputerEngineSettingsService;
import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.infrastructure.engine.uci.UciEngineConfiguration;
import com.escontrela.lastmove.infrastructure.engine.uci.UciProcessEngine;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Creates independent Maia UCI processes for one playing-strength profile.
 *
 * <p>The adapter reuses the generic {@link UciProcessEngine} unchanged: the profile only supplies
 * the {@code lc0} command plus its weights argument. The startup timeout is deliberately generous
 * because loading a neural network and initialising the backend can exceed the desktop defaults.
 */
public final class MaiaComputerMoveEngineProvider implements ComputerMoveEngineProvider {

  private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(60);
  private static final Duration SEARCH_RESPONSE_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

  private final MaiaEngineProfile profile;
  private final ComputerEngineDescriptor descriptor;
  private final ComputerEngineSettingsService settingsService;
  private final FenService fenService;
  private final MaiaExecutableResolver executableResolver;
  private final int threads;

  public MaiaComputerMoveEngineProvider(
      MaiaEngineProfile profile,
      ComputerEngineSettingsService settingsService,
      FenService fenService,
      MaiaExecutableResolver executableResolver,
      int threads) {
    this.profile = Objects.requireNonNull(profile, "profile must not be null");
    this.settingsService = Objects.requireNonNull(settingsService, "settingsService must not be null");
    this.fenService = Objects.requireNonNull(fenService, "fenService must not be null");
    this.executableResolver =
        Objects.requireNonNull(executableResolver, "executableResolver must not be null");
    if (threads < 1) {
      throw new IllegalArgumentException("threads must be positive");
    }
    this.threads = threads;
    this.descriptor =
        new ComputerEngineDescriptor(profile.id(), profile.displayName(), profile.version());
  }

  @Override
  public ComputerEngineDescriptor descriptor() {
    return descriptor;
  }

  @Override
  public ComputerMoveEngine create() {
    return create(runtime());
  }

  ComputerMoveEngine create(MaiaRuntimeDetails runtime) {
    List<String> command =
        List.of(
            runtime.executable().toString(),
            "--weights=" + runtime.weightsFile(),
            "--threads=" + threads);
    UciEngineConfiguration configuration =
        new UciEngineConfiguration(
            descriptor,
            command,
            Optional.empty(),
            Map.of(),
            STARTUP_TIMEOUT,
            SEARCH_RESPONSE_TIMEOUT,
            SHUTDOWN_TIMEOUT);
    return new UciProcessEngine(configuration, fenService);
  }

  /** Resolves and validates the executable and weights file for this profile. */
  public MaiaRuntimeDetails runtime() {
    return executableResolver.resolve(
        settingsService.maiaExecutable().orElse(null),
        settingsService.maiaWeightsLocation(),
        profile);
  }
}
