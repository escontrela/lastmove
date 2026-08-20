package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.CastlingRights;
import com.escontrela.lastmove.domain.game.ChessRulesEngine;
import com.escontrela.lastmove.domain.game.PositionPiece;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.service.FenService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * The single, non-persistent authoring session for the position editor.
 * Editing deliberately accepts incomplete positions; only FEN output requires validation.
 */
@Service
public class PositionEditorService {
  /** Immutable view of the editor state exposed by this service. */
  public record PositionEditorState(PositionSnapshot position, List<String> validationErrors) {
    public PositionEditorState {
      Objects.requireNonNull(position, "position must not be null");
      validationErrors = List.copyOf(Objects.requireNonNull(validationErrors, "validationErrors must not be null"));
    }

    public boolean valid() { return validationErrors.isEmpty(); }

    public Optional<String> validationMessage() {
      return valid() ? Optional.empty() : Optional.of(String.join(" ", validationErrors));
    }
  }

  private final ChessRulesEngine rulesEngine;
  private final FenService fenService;
  private PositionSnapshot position;

  public PositionEditorService(ChessRulesEngine rulesEngine, FenService fenService) {
    this.rulesEngine = Objects.requireNonNull(rulesEngine, "rulesEngine must not be null");
    this.fenService = Objects.requireNonNull(fenService, "fenService must not be null");
    // The editor opens as a blank canvas. The normal starting formation remains an explicit action.
    this.position = snapshot(List.of(), PieceColor.WHITE, CastlingRights.none(), Optional.empty(), 0, 1);
  }

  public PositionEditorState state() { return new PositionEditorState(position, validate(position)); }
  public void reset() { position = rulesEngine.startingPosition(); }
  public void clear() { position = snapshot(List.of(), PieceColor.WHITE, CastlingRights.none(), Optional.empty(), 0, 1); }
  public void place(Square square, PieceType type, PieceColor color) {
    List<PositionPiece> pieces = without(square);
    pieces.add(new PositionPiece(square, type, color));
    position = snapshot(pieces, position.activeColor(), position.castlingRights(), position.enPassantTarget(), position.halfmoveClock(), position.fullmoveNumber());
  }
  public void remove(Square square) { position = snapshot(without(square), position.activeColor(), position.castlingRights(), position.enPassantTarget(), position.halfmoveClock(), position.fullmoveNumber()); }
  public void move(Square from, Square to) {
    PositionPiece piece = position.pieces().stream().filter(p -> p.square().equals(from)).findFirst().orElse(null);
    if (piece == null) return;
    List<PositionPiece> pieces = without(to);
    pieces.removeIf(p -> p.square().equals(from));
    pieces.add(new PositionPiece(to, piece.type(), piece.color()));
    position = snapshot(pieces, position.activeColor(), position.castlingRights(), position.enPassantTarget(), position.halfmoveClock(), position.fullmoveNumber());
  }
  public void configure(PieceColor activeColor, CastlingRights rights, Optional<Square> enPassant, int halfmove, int fullmove) {
    position = snapshot(position.pieces(), activeColor, rights, enPassant, Math.max(0, halfmove), Math.max(1, fullmove));
  }
  public void importFen(String rawFen) { position = rulesEngine.positionFrom(Fen.of(rawFen.trim())); }
  public List<Square> enPassantTargets(PieceColor activeColor) {
    return enPassantTargets(position, Objects.requireNonNull(activeColor, "activeColor must not be null"));
  }
  public Fen fen() {
    PositionEditorState current = state();
    if (!current.valid()) throw new IllegalStateException(current.validationMessage().orElse("Invalid position"));
    return fenService.fromSnapshot(position);
  }

  private List<PositionPiece> without(Square square) {
    return new ArrayList<>(position.pieces().stream().filter(piece -> !piece.square().equals(square)).toList());
  }
  private PositionSnapshot snapshot(List<PositionPiece> pieces, PieceColor active, CastlingRights rights, Optional<Square> ep, int halfmove, int fullmove) {
    return new PositionSnapshot(pieces, active, rights, ep, halfmove, fullmove, Optional.empty(), false, false, false);
  }
  private static List<String> validate(PositionSnapshot value) {
    List<String> errors = new ArrayList<>();
    long whiteKings = value.pieces().stream().filter(p -> p.type() == PieceType.KING && p.color() == PieceColor.WHITE).count();
    long blackKings = value.pieces().stream().filter(p -> p.type() == PieceType.KING && p.color() == PieceColor.BLACK).count();
    if (whiteKings != 1 || blackKings != 1) errors.add("The position must contain exactly one king of each colour.");
    if (value.pieces().stream().anyMatch(p -> p.type() == PieceType.PAWN && (p.square().getRank() == 0 || p.square().getRank() == 7))) errors.add("Pawns cannot be placed on the first or eighth rank.");
    value.enPassantTarget().ifPresent(square -> {
      int expectedRank = value.activeColor() == PieceColor.BLACK ? 2 : 5;
      if (square.getRank() != expectedRank) errors.add("The en passant target is inconsistent with the side to move.");
      if (value.pieces().stream().anyMatch(piece -> piece.square().equals(square))) errors.add("The en passant target square must be empty.");
      if (!enPassantTargets(value, value.activeColor()).contains(square)) errors.add("No pawn can capture on the selected en passant square.");
    });
    CastlingRights rights = value.castlingRights();
    if ((rights.whiteKingSide() || rights.whiteQueenSide()) && !has(value, "e1", PieceType.KING, PieceColor.WHITE)) errors.add("White castling requires a white king on e1.");
    if (rights.whiteKingSide() && !has(value, "h1", PieceType.ROOK, PieceColor.WHITE)) errors.add("White king-side castling requires a rook on h1.");
    if (rights.whiteQueenSide() && !has(value, "a1", PieceType.ROOK, PieceColor.WHITE)) errors.add("White queen-side castling requires a rook on a1.");
    if ((rights.blackKingSide() || rights.blackQueenSide()) && !has(value, "e8", PieceType.KING, PieceColor.BLACK)) errors.add("Black castling requires a black king on e8.");
    if (rights.blackKingSide() && !has(value, "h8", PieceType.ROOK, PieceColor.BLACK)) errors.add("Black king-side castling requires a rook on h8.");
    if (rights.blackQueenSide() && !has(value, "a8", PieceType.ROOK, PieceColor.BLACK)) errors.add("Black queen-side castling requires a rook on a8.");
    return errors;
  }
  private static boolean has(PositionSnapshot position, String square, PieceType type, PieceColor color) {
    return position.pieces().stream().anyMatch(p -> p.square().equals(Square.of(square)) && p.type() == type && p.color() == color);
  }

  private static List<Square> enPassantTargets(PositionSnapshot value, PieceColor activeColor) {
    PieceColor movedColor = activeColor == PieceColor.WHITE ? PieceColor.BLACK : PieceColor.WHITE;
    int pawnRank = activeColor == PieceColor.WHITE ? 4 : 3;
    int targetRank = activeColor == PieceColor.WHITE ? 5 : 2;
    List<Square> candidates = new ArrayList<>();
    for (PositionPiece movedPawn : value.pieces()) {
      if (movedPawn.type() != PieceType.PAWN
          || movedPawn.color() != movedColor
          || movedPawn.square().getRank() != pawnRank) {
        continue;
      }
      int file = movedPawn.square().getFile();
      Square target = Square.of(file, targetRank);
      boolean targetEmpty = value.pieces().stream().noneMatch(piece -> piece.square().equals(target));
      boolean adjacentCapturer =
          value.pieces().stream()
              .anyMatch(
                  piece ->
                      piece.type() == PieceType.PAWN
                          && piece.color() == activeColor
                          && piece.square().getRank() == pawnRank
                          && Math.abs(piece.square().getFile() - file) == 1);
      if (targetEmpty && adjacentCapturer) {
        candidates.add(target);
      }
    }
    return List.copyOf(candidates);
  }
}
