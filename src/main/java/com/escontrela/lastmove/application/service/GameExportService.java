package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.domain.game.Game;
import org.springframework.stereotype.Service;

/**
 * Application service for exporting a game to PGN format.
 *
 * <p>Placeholder – export functionality is planned for a future milestone.
 */
@Service
public class GameExportService {

    /**
     * Exports the given game to a PGN string.
     *
     * @param game the game to export
     * @return a PGN-formatted string representation of the game
     */
    public String exportToPgn(Game game) {
        // TODO: implement PGN export using ChesspressoGameMapper
        throw new UnsupportedOperationException("GameExportService.exportToPgn is not yet implemented");
    }
}
