CREATE TABLE studies (
    id TEXT PRIMARY KEY,
    owner_player_id INTEGER NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    display_order INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (owner_player_id) REFERENCES players(id) ON DELETE CASCADE
);

CREATE INDEX idx_studies_owner_order ON studies(owner_player_id, display_order);

CREATE TABLE study_chapters (
    id TEXT PRIMARY KEY,
    study_id TEXT NOT NULL,
    title TEXT NOT NULL,
    origin TEXT NOT NULL,
    initial_fen TEXT NOT NULL,
    source_result TEXT,
    display_order INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (study_id) REFERENCES studies(id) ON DELETE CASCADE
);

CREATE INDEX idx_chapters_study_order ON study_chapters(study_id, display_order);

CREATE TABLE study_chapter_nodes (
    id TEXT PRIMARY KEY,
    chapter_id TEXT NOT NULL,
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
    FOREIGN KEY (chapter_id) REFERENCES study_chapters(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_node_id) REFERENCES study_chapter_nodes(id) ON DELETE CASCADE
);

CREATE INDEX idx_nodes_chapter_parent_order
    ON study_chapter_nodes(chapter_id, parent_node_id, display_order);

CREATE TABLE study_chapter_navigation (
    chapter_id TEXT PRIMARY KEY,
    current_node_id TEXT,
    selected_root_node_id TEXT,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (chapter_id) REFERENCES study_chapters(id) ON DELETE CASCADE
);

CREATE TABLE study_chapter_selected_continuations (
    chapter_id TEXT NOT NULL,
    parent_node_id TEXT NOT NULL,
    selected_child_node_id TEXT NOT NULL,
    PRIMARY KEY (chapter_id, parent_node_id),
    FOREIGN KEY (chapter_id) REFERENCES study_chapters(id) ON DELETE CASCADE
);