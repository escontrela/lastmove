package com.escontrela.lastmove.infrastructure.engine.sunfish;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerEngineException;
import com.escontrela.lastmove.application.computer.ComputerMoveEngine;
import com.escontrela.lastmove.application.computer.ComputerMoveEngineProvider;
import com.escontrela.lastmove.application.service.ComputerEngineSettingsService;
import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.infrastructure.engine.uci.UciEngineConfiguration;
import com.escontrela.lastmove.infrastructure.engine.uci.UciProcessEngine;
import java.nio.file.Files;
import java.nio.file.Path;
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

  public SunfishComputerMoveEngineProvider(
      ComputerEngineSettingsService settingsService, FenService fenService) {
    this.settingsService =
        Objects.requireNonNull(settingsService, "settingsService must not be null");
    this.fenService = Objects.requireNonNull(fenService, "fenService must not be null");
  }

  @Override
  public ComputerEngineDescriptor descriptor() {
    return DESCRIPTOR;
  }

  @Override
  public ComputerMoveEngine create() {
    Path executable = settingsService.sunfishSettings().executablePath();
    if (!Files.isRegularFile(executable)) {
      throw new ComputerEngineException("Sunfish executable was not found: " + executable);
    }
    if (!Files.isExecutable(executable)) {
      throw new ComputerEngineException("Sunfish executable is not executable: " + executable);
    }
    return new UciProcessEngine(
        UciEngineConfiguration.of(DESCRIPTOR, List.of(executable.toString())), fenService);
  }
}
