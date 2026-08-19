package com.escontrela.lastmove.infrastructure.engine.maia;

import com.escontrela.lastmove.application.computer.ComputerEngineIds;
import java.util.Objects;

/**
 * One Maia playing strength, expressed as a distinct Leela Chess Zero weights file.
 *
 * <p>Each profile shares the same {@code lc0} executable but loads a different neural network, so
 * the engine exposed to the application differs only by its {@link #weightsFileName()}. The
 * {@code version} field carries the Elo label so the setup selector renders a stable, distinct
 * identity such as {@code Maia 1100}.
 */
public enum MaiaEngineProfile {

  MAIA_1100(ComputerEngineIds.MAIA_1100, "Maia", "1100", "maia-1100.pb.gz"),
  MAIA_1500(ComputerEngineIds.MAIA_1500, "Maia", "1500", "maia-1500.pb.gz"),
  MAIA_1700(ComputerEngineIds.MAIA_1700, "Maia", "1700", "maia-1700.pb.gz"),
  MAIA_1900(ComputerEngineIds.MAIA_1900, "Maia", "1900", "maia-1900.pb.gz");

  private final String id;
  private final String displayName;
  private final String version;
  private final String weightsFileName;

  MaiaEngineProfile(String id, String displayName, String version, String weightsFileName) {
    this.id = requireText(id, "id");
    this.displayName = requireText(displayName, "displayName");
    this.version = requireText(version, "version");
    this.weightsFileName = requireText(weightsFileName, "weightsFileName");
  }

  public String id() {
    return id;
  }

  public String displayName() {
    return displayName;
  }

  public String version() {
    return version;
  }

  public String weightsFileName() {
    return weightsFileName;
  }

  private static String requireText(String value, String field) {
    String required = Objects.requireNonNull(value, field + " must not be null").trim();
    if (required.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return required;
  }
}
