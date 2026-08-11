package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.notation.PgnGame;
import java.util.List;
import java.util.Objects;

/** Engine-neutral PGN metadata and its complete tree of imported moves. */
public record ImportedPgnGame(PgnGame game, List<ImportedPly> rootVariations) {

  public ImportedPgnGame {
    Objects.requireNonNull(game, "game must not be null");
    rootVariations = List.copyOf(Objects.requireNonNull(rootVariations, "rootVariations must not be null"));
  }
}
