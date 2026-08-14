package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.common.PieceColor;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Consent workflow for rectifying one or more plies of a progressive game.
 *
 * <p>The game creates the request anchored to its current last ply. Only the opponent may accept or
 * reject it, and the game applies it only while that anchor is still current. This prevents a late
 * response from undoing moves played after the original request.
 */
public final class TakebackRequest {

  private final UUID id;
  private final GameId gameId;
  private final PieceColor requestedBy;
  private final int pliesToUndo;
  private final UUID expectedLastPlyId;
  private TakebackStatus status = TakebackStatus.PENDING;
  private PieceColor respondedBy;

  TakebackRequest(
      GameId gameId, PieceColor requestedBy, int pliesToUndo, UUID expectedLastPlyId) {
    this.id = UUID.randomUUID();
    this.gameId = Objects.requireNonNull(gameId, "gameId must not be null");
    this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy must not be null");
    if (pliesToUndo < 1) {
      throw new IllegalArgumentException("pliesToUndo must be at least one");
    }
    this.pliesToUndo = pliesToUndo;
    this.expectedLastPlyId =
        Objects.requireNonNull(expectedLastPlyId, "expectedLastPlyId must not be null");
  }

  /** Accepts the request on behalf of the requesting player's opponent. */
  public void accept(PieceColor responder) {
    respond(responder, TakebackStatus.ACCEPTED);
  }

  /** Rejects the request on behalf of the requesting player's opponent. */
  public void reject(PieceColor responder) {
    respond(responder, TakebackStatus.REJECTED);
  }

  public UUID id() {
    return id;
  }

  public GameId gameId() {
    return gameId;
  }

  public PieceColor requestedBy() {
    return requestedBy;
  }

  public int pliesToUndo() {
    return pliesToUndo;
  }

  public UUID expectedLastPlyId() {
    return expectedLastPlyId;
  }

  public TakebackStatus status() {
    return status;
  }

  public Optional<PieceColor> respondedBy() {
    return Optional.ofNullable(respondedBy);
  }

  void markApplied() {
    if (status != TakebackStatus.ACCEPTED) {
      throw new IllegalStateException("Only an accepted takeback can be applied");
    }
    status = TakebackStatus.APPLIED;
  }

  private void respond(PieceColor responder, TakebackStatus response) {
    PieceColor required = Objects.requireNonNull(responder, "responder must not be null");
    if (status != TakebackStatus.PENDING) {
      throw new IllegalStateException("The takeback request has already been answered");
    }
    if (required != requestedBy.opposite()) {
      throw new IllegalArgumentException("Only the opponent may answer a takeback request");
    }
    respondedBy = required;
    status = response;
  }
}
