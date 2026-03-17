ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS push_enabled BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN devices.push_enabled IS '푸시 수신 여부';
