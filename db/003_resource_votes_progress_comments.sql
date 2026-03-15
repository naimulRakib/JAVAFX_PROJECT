CREATE TABLE IF NOT EXISTS resource_votes (
    user_id UUID NOT NULL,
    resource_id INTEGER NOT NULL,
    vote_type INTEGER NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (user_id, resource_id),
    FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_progress (
    user_id UUID NOT NULL,
    resource_id INTEGER NOT NULL,
    is_completed BOOLEAN DEFAULT TRUE,
    difficulty_rating TEXT,
    time_spent_mins INTEGER,
    user_note TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (user_id, resource_id),
    FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS comments (
    id BIGSERIAL PRIMARY KEY,
    resource_id INTEGER NOT NULL,
    user_id UUID,
    content TEXT NOT NULL,
    parent_id INTEGER,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_resource_votes_resource
    ON resource_votes (resource_id);
CREATE INDEX IF NOT EXISTS idx_user_progress_resource
    ON user_progress (resource_id);
CREATE INDEX IF NOT EXISTS idx_comments_resource
    ON comments (resource_id);
