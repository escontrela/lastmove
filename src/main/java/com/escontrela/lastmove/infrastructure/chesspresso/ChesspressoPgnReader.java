package com.escontrela.lastmove.infrastructure.chesspresso;

import chesspresso.game.Game;
import chesspresso.Chess;
import chesspresso.move.Move;
import chesspresso.pgn.PGNReader;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.ImportedPgnGame;
import com.escontrela.lastmove.domain.game.ImportedPly;
import com.escontrela.lastmove.domain.game.MoveDescriptor;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.notation.PgnGame;
import com.escontrela.lastmove.domain.notation.SanMove;
import java.io.InputStream;
import java.io.StringReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Reads PGN data using the Chesspresso library and produces {@link PgnGame} instances.
 *
 * <p>This class is the only entry point through which Chesspresso's PGN parsing is used. Domain and
 * application classes must not depend on Chesspresso types directly.
 */
@Component
public class ChesspressoPgnReader {

  /**
   * Reads the first game from the given PGN string.
   *
   * @param pgn raw PGN text
   * @return a {@link PgnGame} populated from the parsed Chesspresso game
   * @throws Exception if parsing fails
   */
  public PgnGame readFirst(String pgn) throws Exception {
    return readImportedFirst(pgn).game();
  }

  /** Reads the first game and all of its continuations as an engine-neutral move tree. */
  public ImportedPgnGame readImportedFirst(String pgn) throws Exception {
    PGNReader reader = new PGNReader(new StringReader(pgn), "inline");
    return toImportedGame(reader.parseGame());
  }

  /**
   * Reads the first game from the given input stream (e.g. a .pgn file).
   *
   * @param inputStream source stream containing PGN data
   * @param sourceName a label used in error messages
   * @return a {@link PgnGame} populated from the parsed Chesspresso game
   * @throws Exception if parsing fails
   */
  public PgnGame readFirst(InputStream inputStream, String sourceName) throws Exception {
    PGNReader reader = new PGNReader(inputStream, sourceName);
    return toImportedGame(reader.parseGame()).game();
  }

  /** Reads the first game and all of its continuations from a stream. */
  public ImportedPgnGame readImportedFirst(InputStream inputStream, String sourceName) throws Exception {
    return toImportedGame(new PGNReader(inputStream, sourceName).parseGame());
  }

  /** Reads an imported PGN tree from a filesystem path. */
  public ImportedPgnGame readImportedFirst(Path path) throws Exception {
    try (InputStream inputStream = Files.newInputStream(path)) {
      return readImportedFirst(inputStream, path.getFileName().toString());
    } catch (IOException exception) {
      throw new IllegalArgumentException("Unable to read PGN file: " + path, exception);
    }
  }

  private ImportedPgnGame toImportedGame(Game game) {
    game.gotoStart();
    return new ImportedPgnGame(ChesspressoGameMapper.toPgnGame(game), importVariations(game));
  }

  private List<ImportedPly> importVariations(Game game) {
    List<ImportedPly> variations = new ArrayList<>();
    for (int variation = 0; variation < game.getNumOfNextMoves(); variation++) {
      game.goForward(variation);
      Move move = game.getLastMove();
      short shortMove = move.getShortMoveDesc();
      MoveDescriptor descriptor =
          new MoveDescriptor(
              Square.of(Chess.sqiToCol(Move.getFromSqi(shortMove)), Chess.sqiToRow(Move.getFromSqi(shortMove))),
              Square.of(Chess.sqiToCol(Move.getToSqi(shortMove)), Chess.sqiToRow(Move.getToSqi(shortMove))),
              SanMove.of(move.getSAN()),
              move.isCapturing(),
              move.isShortCastle() || move.isLongCastle(),
              Move.isEPMove(shortMove),
              move.isPromotion() ? Optional.of(toPieceType(move.getPromo())) : Optional.empty());
      PositionSnapshot snapshot =
          ChesspressoPositionSnapshotMapper.fromPosition(game.getPosition(), Optional.of(descriptor));
      variations.add(
          new ImportedPly(
              new MoveExecutionResult(
                  true,
                  Optional.empty(),
                  snapshot,
                  Optional.of(descriptor),
                  Optional.empty(),
                  snapshot.check(),
                  snapshot.mate(),
                  snapshot.stalemate(),
                  List.of()),
              importVariations(game)));
      game.goBack();
    }
    return List.copyOf(variations);
  }

  private PieceType toPieceType(int piece) {
    return switch (piece) {
      case Chess.KING -> PieceType.KING;
      case Chess.QUEEN -> PieceType.QUEEN;
      case Chess.ROOK -> PieceType.ROOK;
      case Chess.BISHOP -> PieceType.BISHOP;
      case Chess.KNIGHT -> PieceType.KNIGHT;
      case Chess.PAWN -> PieceType.PAWN;
      default -> throw new IllegalArgumentException("Unsupported Chesspresso piece: " + piece);
    };
  }
}
