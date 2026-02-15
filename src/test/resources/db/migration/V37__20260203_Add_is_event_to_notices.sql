ALTER TABLE notices
    ADD COLUMN IF NOT EXISTS is_event BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS IDX_notices_is_event ON notices(is_event);
