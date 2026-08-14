package com.escontrela.lastmove.domain.notation;

import com.escontrela.lastmove.domain.game.GameResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A parsed PGN game consisting of its header tags, the raw move-text, and the game result.
 *
 * <p>PgnGame is a data-holder produced by the infrastructure layer (Chesspresso) and consumed
 * by the domain and application layers. It does not depend on any third-party chess library.
 */
public class PgnGame {

    private final Map<String, String> headers;
    private final String moveText;
    private final GameResult result;
    private final Fen startingFen;

    public PgnGame(Map<String, String> headers, String moveText, GameResult result, Fen startingFen) {
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(headers, "headers must not be null")));
        this.moveText = moveText;
        this.result = result;
        this.startingFen = startingFen;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Optional<String> getHeader(String tag) {
        return Optional.ofNullable(headers.get(tag));
    }

    public String getMoveText() {
        return moveText;
    }

    public GameResult getResult() {
        return result;
    }

    public Optional<Fen> getStartingFen() {
        return Optional.ofNullable(startingFen);
    }

    public Optional<String> getWhite() {
        return getHeader("White");
    }

    public Optional<String> getBlack() {
        return getHeader("Black");
    }

    public Optional<String> getEvent() {
        return getHeader("Event");
    }

    public Optional<String> getDate() {
        return getHeader("Date");
    }

    /** Returns a concise user-facing title derived from this PGN game's own headers. */
    public String displayTitle() {
        return getWhite().orElse("?") + " vs. " + getBlack().orElse("?")
                + " – " + getEvent().orElse("?");
    }
}
