package com.escontrela.lastmove.infrastructure.engine.sunfish;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerMoveEngine;
import com.escontrela.lastmove.application.computer.ComputerMoveEngineProvider;
import com.escontrela.lastmove.application.service.ComputerEngineSettingsService;
import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.infrastructure.engine.uci.UciEngineConfiguration;
import com.escontrela.lastmove.infrastructure.engine.uci.UciProcessEngine;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Creates independent Sunfish UCI processes using the executable configured by the user. */
@Component
public final class SunfishComputerMoveEngineProvider implements ComputerMoveEngineProvider {

  private static final ComputerEngineDescriptor DESCRIPTOR =
      new ComputerEngineDescriptor("sunfish", "Sunfish", "2026");

  private final ComputerEngineSettingsService settingsService;
  private final FenService fenService;
  private final SunfishExecutableResolver executableResolver;

  public SunfishComputerMoveEngineProvider(
      ComputerEngineSettingsService settingsService,
      FenService fenService,
      SunfishExecutableResolver executableResolver) {
    this.settingsService =
        Objects.requireNonNull(settingsService, "settingsService must not be null");
    this.fenService = Objects.requireNonNull(fenService, "fenService must not be null");
    this.executableResolver =
        Objects.requireNonNull(executableResolver, "executableResolver must not be null");
  }

  @Override
  public ComputerEngineDescriptor descriptor() {
    return DESCRIPTOR;
  }

  @Override
  public ComputerMoveEngine create() {
    return create(runtime());
  }

  ComputerMoveEngine create(SunfishRuntimeDetails runtime) {
    return new UciProcessEngine(
        UciEngineConfiguration.of(DESCRIPTOR, List.of(runtime.executable().toString())), fenService);
  }

  /** Resolves and validates the wrapper and Python runtime currently selected in Settings. */
  public SunfishRuntimeDetails runtime() {
    return executableResolver.resolve(settingsService.sunfishSettings().executablePath());
  }
}
