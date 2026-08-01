package com.escontrela.lastmove.infrastructure.chesspresso;

import org.chesspresso.game.Game;
import org.chesspresso.pgn.PGNReader;
import com.escontrela.lastmove.domain.game.MoveTree;
import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.notation.PgnGame;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads PGN data using the Chesspresso library and produces {@link PgnGame} instances.
 *
 * <p>This class is the only entry point through which Chesspresso's PGN parsing
 * is used. Domain and application classes must not depend on Chesspresso types directly.
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
        PGNReader reader = new PGNReader(new StringReader(pgn), "inline");
        Game chesspressoGame = reader.parseGame();
        return ChesspressoGameMapper.toPgnGame(chesspressoGame);
    }

    /**
     * Reads the first game from the given input stream (e.g. a .pgn file).
     *
     * @param inputStream source stream containing PGN data
     * @param sourceName  a label used in error messages
     * @return a {@link PgnGame} populated from the parsed Chesspresso game
     * @throws Exception if parsing fails
     */
    public PgnGame readFirst(InputStream inputStream, String sourceName) throws Exception {
        PGNReader reader = new PGNReader(inputStream, sourceName);
        Game chesspressoGame = reader.parseGame();
        return ChesspressoGameMapper.toPgnGame(chesspressoGame);
    }
}
