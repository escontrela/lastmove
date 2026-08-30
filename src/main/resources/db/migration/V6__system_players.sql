ALTER TABLE players ADD COLUMN player_type TEXT NOT NULL DEFAULT 'HUMAN';
ALTER TABLE players ADD COLUMN external_provider TEXT;
ALTER TABLE players ADD COLUMN external_account_id TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_players_external_identity
    ON players(external_provider, external_account_id)
    WHERE external_provider IS NOT NULL AND external_account_id IS NOT NULL;
