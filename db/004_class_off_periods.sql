CREATE TABLE IF NOT EXISTS class_off_periods (
    id BIGSERIAL PRIMARY KEY,
    channel_id INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    reason TEXT,
    created_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_class_off_channel_dates
    ON class_off_periods (channel_id, start_date, end_date);

-- Example inserts (day/month/year):
-- Single day:
-- INSERT INTO class_off_periods (channel_id, start_date, end_date, reason, created_by)
-- VALUES (1, to_date('13/3/2026','DD/MM/YYYY'), to_date('13/3/2026','DD/MM/YYYY'),
--         'Varsity off', '00000000-0000-0000-0000-000000000000');
--
-- Range:
-- INSERT INTO class_off_periods (channel_id, start_date, end_date, reason, created_by)
-- VALUES (1, to_date('13/3/2026','DD/MM/YYYY'), to_date('25/3/2026','DD/MM/YYYY'),
--         'Varsity off (range)', '00000000-0000-0000-0000-000000000000');
