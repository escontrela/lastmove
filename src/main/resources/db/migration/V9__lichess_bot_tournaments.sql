CREATE TABLE IF NOT EXISTS lichess_tournaments (
    lichess_tournament_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    remote_status TEXT NOT NULL,
    variant TEXT NOT NULL,
    rated INTEGER NOT NULL,
    clock_limit_seconds INTEGER NOT NULL,
    clock_increment_seconds INTEGER NOT NULL,
    duration_minutes INTEGER NOT NULL,
    player_count INTEGER NOT NULL,
    minimum_rating INTEGER,
    maximum_rating INTEGER,
    bots_allowed INTEGER NOT NULL,
    starts_at INTEGER,
    finishes_at INTEGER,
    seconds_to_start INTEGER,
    tournament_url TEXT,
    registration_status TEXT NOT NULL,
    last_error TEXT,
    last_seen_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_lichess_tournaments_updated ON lichess_tournaments(updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_lichess_tournaments_registration ON lichess_tournaments(registration_status, updated_at DESC);
