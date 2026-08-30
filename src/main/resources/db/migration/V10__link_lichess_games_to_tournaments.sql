ALTER TABLE lichess_games ADD COLUMN tournament_id TEXT;

CREATE INDEX IF NOT EXISTS idx_lichess_games_tournament_id
    ON lichess_games(tournament_id);
