-- 디바이스 로그인 추적 테이블
CREATE TABLE devices (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    device_type VARCHAR(16) NOT NULL,
    device_os VARCHAR(16) NOT NULL,
    app_version VARCHAR(32) NULL,
    user_agent VARCHAR(512) NULL,
    last_ip VARCHAR(64) NULL,
    last_login_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT PK_devices PRIMARY KEY (id),
    CONSTRAINT FK_devices_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

COMMENT ON TABLE devices IS '유저 로그인 디바이스';
COMMENT ON COLUMN devices.user_id IS '유저 ID';
COMMENT ON COLUMN devices.device_id IS '클라이언트 디바이스 ID';
COMMENT ON COLUMN devices.device_type IS '디바이스 타입 (WEB/MOBILE)';
COMMENT ON COLUMN devices.device_os IS '디바이스 OS (WEB/IOS/ANDROID)';
COMMENT ON COLUMN devices.app_version IS '앱 버전';
COMMENT ON COLUMN devices.user_agent IS 'User-Agent';
COMMENT ON COLUMN devices.last_ip IS '마지막 로그인 IP';
COMMENT ON COLUMN devices.last_login_at IS '마지막 로그인 시간';

CREATE INDEX idx_devices_user_id ON devices(user_id);
CREATE UNIQUE INDEX ux_devices_user_type_active ON devices(user_id, device_type) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_devices_user_device_active ON devices(user_id, device_id) WHERE deleted_at IS NULL;
