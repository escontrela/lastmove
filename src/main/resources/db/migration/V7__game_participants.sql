-- A game can be visible in more than one player's history. Keep owner_player_id
-- on games for compatibility and backfill it as the first participant.
CREATE TABLE IF NOT EXISTS game_participants (
    game_id TEXT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    player_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    participant_role TEXT NOT NULL DEFAULT 'PLAYER',
    joined_at INTEGER NOT NULL,
    PRIMARY KEY (game_id, player_id)
);
CREATE INDEX IF NOT EXISTS idx_game_participants_player ON game_participants(player_id, game_id);

INSERT OR IGNORE INTO game_participants(game_id, player_id, participant_role, joined_at)
SELECT id, owner_player_id, 'OWNER', updated_at
FROM games
WHERE owner_player_id IS NOT NULL;
