package com.escontrela.lastmove.infrastructure.chesspresso;

import chesspresso.game.Game;
import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.game.MoveTree;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.notation.PgnGame;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps Chesspresso {@link Game} objects to LastMove domain objects.
 *
 * <p>All Chesspresso types are confined to this class. No other package imports Chesspresso classes
 * except {@link ChesspressoPgnReader} and {@link ChesspressoFenMapper}.
 */
public final class ChesspressoGameMapper {

  private ChesspressoGameMapper() {}

  /**
   * Converts a Chesspresso {@link Game} to a {@link PgnGame}.
   *
   * @param game the Chesspresso game to convert
   * @return a LastMove {@link PgnGame}
   */
  public static PgnGame toPgnGame(Game game) {
    Map<String, String> headers = new LinkedHashMap<>();

    // Standard seven-tag roster
    addHeader(headers, "Event", game.getEvent());
    addHeader(headers, "Site", game.getSite());
    addHeader(headers, "Date", game.getDate());
    addHeader(headers, "Round", game.getRound());
    addHeader(headers, "White", game.getWhite());
    addHeader(headers, "Black", game.getBlack());
    addHeader(headers, "Result", game.getResultStr());

    GameResult result = GameResult.fromPgn(game.getResultStr() != null ? game.getResultStr() : "*");

    String fen = game.getTag("FEN");
    return new PgnGame(headers, "", result, fen == null || fen.isBlank() ? null : Fen.of(fen));
  }

  /**
   * Builds a {@link MoveTree} from a Chesspresso {@link Game}.
   *
   * <p>Placeholder – full move-tree extraction will be implemented in a future milestone.
   *
   * @param game the Chesspresso game to extract moves from
   * @return a (currently empty) {@link MoveTree}
   */
  public static MoveTree toMoveTree(Game game) {
    // TODO: traverse Chesspresso move model and populate the MoveTree
    return new MoveTree();
  }

  private static void addHeader(Map<String, String> map, String key, String value) {
    if (value != null && !value.isEmpty()) {
      map.put(key, value);
    }
  }
}
