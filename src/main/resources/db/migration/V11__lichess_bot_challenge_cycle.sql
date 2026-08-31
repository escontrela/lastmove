CREATE TABLE IF NOT EXISTS lichess_bot_challenge_cycle (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    status TEXT NOT NULL,
    configuration_json TEXT NOT NULL,
    attempted_bot_ids_json TEXT NOT NULL,
    current_bot_id TEXT,
    current_challenge_id TEXT,
    current_game_id TEXT,
    completed_games INTEGER NOT NULL,
    last_result TEXT,
    stop_reason TEXT,
    updated_at INTEGER NOT NULL
);
