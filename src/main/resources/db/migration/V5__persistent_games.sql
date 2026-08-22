CREATE TABLE IF NOT EXISTS games (
    id TEXT PRIMARY KEY,
    owner_player_id INTEGER REFERENCES players(id),
    game_type TEXT NOT NULL,
    status TEXT NOT NULL,
    initial_fen TEXT NOT NULL,
    current_fen TEXT NOT NULL,
    white_name TEXT NOT NULL,
    white_elo INTEGER,
    black_name TEXT NOT NULL,
    black_elo INTEGER,
    white_remaining_ms INTEGER,
    black_remaining_ms INTEGER,
    time_control_initial_ms INTEGER,
    time_control_increment_ms INTEGER NOT NULL,
    result TEXT,
    termination_reason TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_games_owner_updated ON games(owner_player_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS game_moves (
    game_id TEXT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    ply_index INTEGER NOT NULL,
    ply_id TEXT NOT NULL,
    san TEXT NOT NULL,
    move_from TEXT NOT NULL,
    move_to TEXT NOT NULL,
    promotion TEXT,
    capture INTEGER NOT NULL,
    castle INTEGER NOT NULL,
    en_passant INTEGER NOT NULL,
    moving_color TEXT NOT NULL,
    move_number INTEGER NOT NULL,
    resulting_fen TEXT NOT NULL,
    clock_before_white_ms INTEGER,
    clock_before_black_ms INTEGER,
    PRIMARY KEY (game_id, ply_index)
);

CREATE TABLE IF NOT EXISTS computer_game_configuration (
    game_id TEXT PRIMARY KEY REFERENCES games(id) ON DELETE CASCADE,
    human_name TEXT NOT NULL,
    human_color TEXT NOT NULL,
    engine_id TEXT NOT NULL,
    engine_thinking_ms INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS game_notifications (
    id TEXT PRIMARY KEY,
    owner_player_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    game_id TEXT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    kind TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    read_at INTEGER
);
CREATE INDEX IF NOT EXISTS idx_game_notifications_owner_created ON game_notifications(owner_player_id, created_at DESC);
