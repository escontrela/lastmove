CREATE TABLE IF NOT EXISTS tags (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    normalized_name TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS tag_assignments (
    tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    target_type TEXT NOT NULL,
    target_id TEXT NOT NULL,
    assigned_at INTEGER NOT NULL,
    PRIMARY KEY (tag_id, target_type, target_id)
);
CREATE INDEX IF NOT EXISTS idx_tag_assignments_target ON tag_assignments(target_type, target_id);

CREATE TRIGGER IF NOT EXISTS delete_game_tag_assignments
AFTER DELETE ON games
BEGIN
    DELETE FROM tag_assignments WHERE target_type = 'GAME' AND target_id = OLD.id;
END;
