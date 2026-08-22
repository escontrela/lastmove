CREATE TABLE study_comments (
    study_id TEXT PRIMARY KEY,
    comment TEXT NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (study_id) REFERENCES studies(id) ON DELETE CASCADE
);

CREATE TABLE study_chapter_comments (
    chapter_id TEXT PRIMARY KEY,
    comment TEXT NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (chapter_id) REFERENCES study_chapters(id) ON DELETE CASCADE
);

CREATE TABLE study_move_comments (
    chapter_id TEXT NOT NULL,
    node_id TEXT NOT NULL,
    comment TEXT NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (chapter_id, node_id),
    FOREIGN KEY (chapter_id) REFERENCES study_chapters(id) ON DELETE CASCADE,
    FOREIGN KEY (node_id) REFERENCES study_chapter_nodes(id) ON DELETE CASCADE
);
