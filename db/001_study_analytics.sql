CREATE TABLE IF NOT EXISTS study_analytics (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    minutes INTEGER NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_study_analytics_user_date
    ON study_analytics (user_id, created_at);
