CREATE TABLE tactic_suites (
    id TEXT PRIMARY KEY,
    owner_player_id INTEGER NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    display_order INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (owner_player_id) REFERENCES players(id) ON DELETE CASCADE
);

CREATE INDEX idx_tactic_suites_owner_order ON tactic_suites(owner_player_id, display_order);

CREATE TABLE tactic_exercises (
    id TEXT PRIMARY KEY,
    suite_id TEXT NOT NULL,
    title TEXT NOT NULL,
    initial_fen TEXT NOT NULL,
    display_order INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (suite_id) REFERENCES tactic_suites(id) ON DELETE CASCADE
);

CREATE INDEX idx_tactic_exercises_suite_order ON tactic_exercises(suite_id, display_order);

CREATE TABLE tactic_solution_nodes (
    id TEXT PRIMARY KEY,
    exercise_id TEXT NOT NULL,
    parent_node_id TEXT,
    ply_id TEXT NOT NULL,
    move_from TEXT NOT NULL,
    move_to TEXT NOT NULL,
    promotion_piece TEXT,
    san TEXT NOT NULL,
    capture INTEGER NOT NULL,
    castle INTEGER NOT NULL,
    en_passant INTEGER NOT NULL,
    moving_color TEXT NOT NULL,
    move_number INTEGER NOT NULL,
    resulting_fen TEXT NOT NULL,
    display_order INTEGER NOT NULL,
    FOREIGN KEY (exercise_id) REFERENCES tactic_exercises(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_node_id) REFERENCES tactic_solution_nodes(id) ON DELETE CASCADE
);

CREATE INDEX idx_tactic_solution_nodes_exercise_parent_order
    ON tactic_solution_nodes(exercise_id, parent_node_id, display_order);
