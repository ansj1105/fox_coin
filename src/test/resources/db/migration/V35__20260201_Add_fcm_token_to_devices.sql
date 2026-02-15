-- FCM 푸시 알림용 디바이스 토큰
ALTER TABLE devices ADD COLUMN IF NOT EXISTS fcm_token VARCHAR(512) NULL;
COMMENT ON COLUMN devices.fcm_token IS 'FCM 등록 토큰 (푸시 알림 발송용)';

CREATE INDEX IF NOT EXISTS idx_devices_fcm_token ON devices(fcm_token) WHERE fcm_token IS NOT NULL AND deleted_at IS NULL;
