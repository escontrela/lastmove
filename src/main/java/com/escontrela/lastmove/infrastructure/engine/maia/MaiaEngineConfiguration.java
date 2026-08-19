package com.escontrela.lastmove.infrastructure.engine.maia;

import com.escontrela.lastmove.application.service.ComputerEngineSettingsService;
import com.escontrela.lastmove.domain.game.ChessRulesEngine;
import com.escontrela.lastmove.domain.service.FenService;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers one {@link MaiaComputerMoveEngineProvider} and one health check per Maia profile.
 *
 * <p>{@code ComputerGameService} collects every {@code ComputerMoveEngineProvider} bean, so each
 * profile appears automatically in the human-versus-computer setup selector without any UI change.
 * The {@code lc0} executable and weights location are read from settings at launch time; only the
 * weights file differs between profiles.
 */
@Configuration
public class MaiaEngineConfiguration {

  private final ComputerEngineSettingsService settingsService;
  private final FenService fenService;
  private final MaiaExecutableResolver executableResolver;
  private final ChessRulesEngine rulesEngine;
  private final int threads;

  public MaiaEngineConfiguration(
      ComputerEngineSettingsService settingsService,
      FenService fenService,
      MaiaExecutableResolver executableResolver,
      ChessRulesEngine rulesEngine,
      @Value("${lastmove.engine.maia.threads:4}") int threads) {
    this.settingsService = Objects.requireNonNull(settingsService, "settingsService must not be null");
    this.fenService = Objects.requireNonNull(fenService, "fenService must not be null");
    this.executableResolver =
        Objects.requireNonNull(executableResolver, "executableResolver must not be null");
    this.rulesEngine = Objects.requireNonNull(rulesEngine, "rulesEngine must not be null");
    if (threads < 1) {
      throw new IllegalArgumentException("lastmove.engine.maia.threads must be positive");
    }
    this.threads = threads;
  }

  @Bean
  public MaiaComputerMoveEngineProvider maia1100Provider() {
    return provider(MaiaEngineProfile.MAIA_1100);
  }

  @Bean
  public MaiaComputerMoveEngineProvider maia1500Provider() {
    return provider(MaiaEngineProfile.MAIA_1500);
  }

  @Bean
  public MaiaComputerMoveEngineProvider maia1700Provider() {
    return provider(MaiaEngineProfile.MAIA_1700);
  }

  @Bean
  public MaiaComputerMoveEngineProvider maia1900Provider() {
    return provider(MaiaEngineProfile.MAIA_1900);
  }

  @Bean
  public MaiaComputerEngineHealthCheck maia1100HealthCheck() {
    return healthCheck(maia1100Provider());
  }

  @Bean
  public MaiaComputerEngineHealthCheck maia1500HealthCheck() {
    return healthCheck(maia1500Provider());
  }

  @Bean
  public MaiaComputerEngineHealthCheck maia1700HealthCheck() {
    return healthCheck(maia1700Provider());
  }

  @Bean
  public MaiaComputerEngineHealthCheck maia1900HealthCheck() {
    return healthCheck(maia1900Provider());
  }

  private MaiaComputerMoveEngineProvider provider(MaiaEngineProfile profile) {
    return new MaiaComputerMoveEngineProvider(
        profile, settingsService, fenService, executableResolver, threads);
  }

  private MaiaComputerEngineHealthCheck healthCheck(MaiaComputerMoveEngineProvider provider) {
    return new MaiaComputerEngineHealthCheck(provider, rulesEngine);
  }
}
