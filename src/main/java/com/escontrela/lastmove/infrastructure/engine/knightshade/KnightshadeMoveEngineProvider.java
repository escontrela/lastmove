package com.escontrela.lastmove.infrastructure.engine.knightshade;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerEngineIds;
import com.escontrela.lastmove.application.computer.ComputerMoveEngine;
import com.escontrela.lastmove.application.computer.ComputerMoveEngineProvider;
import com.escontrela.lastmove.domain.service.FenService;
import com.knightshade.engine.KnightshadeEngine;
import java.util.Objects;
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

  public KnightshadeMoveEngineProvider(FenService fenService) {
    this.fenService = Objects.requireNonNull(fenService, "fenService must not be null");
  }

  @Override
  public ComputerEngineDescriptor descriptor() {
    return DESCRIPTOR;
  }

  @Override
  public ComputerMoveEngine create() {
    return new KnightshadeMoveEngine(new KnightshadeEngine(), fenService, DESCRIPTOR);
  }
}
