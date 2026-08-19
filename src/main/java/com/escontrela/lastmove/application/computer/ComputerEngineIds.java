package com.escontrela.lastmove.application.computer;

/** Stable identifiers used to persist and resolve supported computer opponents. */
public final class ComputerEngineIds {

  public static final String SUNFISH = "sunfish";

  public static final String KNIGHTSHADE = "knightshade";

  /** Family key shared by every Maia profile; holds the optional {@code lc0} executable override. */
  public static final String MAIA = "maia";

  /** Persistence key for the Maia weights location (a directory of {@code .pb.gz} files or one file). */
  public static final String MAIA_WEIGHTS = "maia.weights";

  public static final String MAIA_1100 = "maia-1100";

  public static final String MAIA_1500 = "maia-1500";

  public static final String MAIA_1700 = "maia-1700";

  public static final String MAIA_1900 = "maia-1900";

  private ComputerEngineIds() {}
}
