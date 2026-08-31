package com.escontrela.lastmove.ui.component.game;

import com.escontrela.lastmove.domain.game.*;
import java.time.Duration;
import java.util.*;

/** Presentation-neutral contract consumed by the reusable board, clocks and notation viewer. */
public record LiveGameViewerState(LiveGameViewerSource source, String sourceLabel, GamePlayer whitePlayer,
    GamePlayer blackPlayer, PositionSnapshot initialPosition, PositionSnapshot position, List<Ply> moves,
    Optional<Duration> whiteClock, Optional<Duration> blackClock, boolean finished,
    Optional<GameResult> result, Optional<GameTerminationReason> terminationReason,
    Optional<String> message) {
  public LiveGameViewerState { source=Objects.requireNonNull(source);sourceLabel=Objects.requireNonNull(sourceLabel);whitePlayer=Objects.requireNonNull(whitePlayer);blackPlayer=Objects.requireNonNull(blackPlayer);initialPosition=Objects.requireNonNull(initialPosition);position=Objects.requireNonNull(position);moves=List.copyOf(Objects.requireNonNull(moves));whiteClock=Objects.requireNonNull(whiteClock);blackClock=Objects.requireNonNull(blackClock);result=Objects.requireNonNull(result);terminationReason=Objects.requireNonNull(terminationReason);message=Objects.requireNonNull(message); }
}
