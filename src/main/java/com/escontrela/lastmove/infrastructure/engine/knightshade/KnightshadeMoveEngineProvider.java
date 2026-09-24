package com.escontrela.lastmove.infrastructure.engine.knightshade;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerEngineIds;
import com.escontrela.lastmove.application.computer.ComputerMoveEngine;
import com.escontrela.lastmove.application.computer.ComputerMoveEngineProvider;
import com.escontrela.lastmove.application.computer.PonderSettings;
import com.escontrela.lastmove.application.service.ComputerEngineSettingsService;
import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.application.service.KnightshadeTelemetryService;
import com.knightshade.engine.KnightshadeEngine;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Creates independent in-process Knightshade engines for each progressive game.
 *
 * <p>Registering this provider as a Spring component is what surfaces Knightshade in the
 * human-versus-computer setup overlay: {@code ComputerGameService} collects every {@link
 * ComputerMoveEngineProvider} bean and exposes its descriptor automatically.
 */
@Component
public final class KnightshadeMoveEngineProvider implements ComputerMoveEngineProvider {

  private static final ComputerEngineDescriptor DESCRIPTOR =
      new ComputerEngineDescriptor(ComputerEngineIds.KNIGHTSHADE, "Knightshade", "v3.5");

  private final FenService fenService;
  private final KnightshadeTelemetryService telemetryService;
  private final Supplier<PonderSettings> ponderSettings;

  public KnightshadeMoveEngineProvider(FenService fenService) {
    this(fenService, new KnightshadeTelemetryService(), PonderSettings::defaults);
  }

  public KnightshadeMoveEngineProvider(FenService fenService, KnightshadeTelemetryService telemetryService) {
    this(fenService, telemetryService, PonderSettings::defaults);
  }

  @Autowired
  public KnightshadeMoveEngineProvider(FenService fenService,
      KnightshadeTelemetryService telemetryService, ComputerEngineSettingsService settingsService) {
    this(fenService, telemetryService, settingsService::ponderSettings);
  }

  private KnightshadeMoveEngineProvider(FenService fenService,
      KnightshadeTelemetryService telemetryService, Supplier<PonderSettings> ponderSettings) {
    this.fenService = Objects.requireNonNull(fenService, "fenService must not be null");
    this.telemetryService = Objects.requireNonNull(telemetryService, "telemetryService must not be null");
    this.ponderSettings = Objects.requireNonNull(ponderSettings, "ponderSettings must not be null");
  }

  @Override
  public ComputerEngineDescriptor descriptor() {
    return DESCRIPTOR;
  }

  @Override
  public ComputerMoveEngine create() {
    return new KnightshadeMoveEngine(new KnightshadeEngine(), fenService, DESCRIPTOR,
        telemetryService, ponderSettings);
  }

  @Override
  public int estimatedSearchWorkers() {
    return Integer.getInteger("knightshade.threads",
        Math.min(4, Runtime.getRuntime().availableProcessors()));
  }
}
