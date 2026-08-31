CREATE TABLE IF NOT EXISTS lichess_friendly_bots (
    lichess_bot_id TEXT PRIMARY KEY,
    username TEXT NOT NULL,
    rating INTEGER,
    first_accepted_at INTEGER NOT NULL,
    last_accepted_at INTEGER NOT NULL
);
