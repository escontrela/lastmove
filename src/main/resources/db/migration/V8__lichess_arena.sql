CREATE TABLE IF NOT EXISTS lichess_arena_connections (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    status TEXT NOT NULL,
    last_error TEXT,
    connected_at INTEGER,
    disconnected_at INTEGER,
    updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS lichess_challenges (
    lichess_challenge_id TEXT PRIMARY KEY,
    challenger_id TEXT,
    challenger_name TEXT NOT NULL,
    challenger_rating INTEGER,
    variant TEXT NOT NULL,
    rated INTEGER NOT NULL,
    clock_limit_seconds INTEGER,
    clock_increment_seconds INTEGER,
    decision TEXT NOT NULL,
    decision_reason TEXT,
    received_at INTEGER NOT NULL,
    decided_at INTEGER,
    updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_lichess_challenges_updated ON lichess_challenges(updated_at DESC);

CREATE TABLE IF NOT EXISTS lichess_games (
    lichess_game_id TEXT PRIMARY KEY,
    local_game_id TEXT REFERENCES games(id) ON DELETE SET NULL,
    challenge_id TEXT REFERENCES lichess_challenges(lichess_challenge_id) ON DELETE SET NULL,
    game_url TEXT,
    white_lichess_id TEXT,
    black_lichess_id TEXT,
    bot_color TEXT,
    remote_status TEXT NOT NULL,
    last_error TEXT,
    started_at INTEGER NOT NULL,
    finished_at INTEGER,
    updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_lichess_games_status ON lichess_games(remote_status, updated_at DESC);
