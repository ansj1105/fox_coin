-- 앱 공통 설정 (최소 버전 등). 키별로 한 행.
-- 운영 중 값만 변경하면 되며, 배포 없이 적용 가능.
CREATE TABLE IF NOT EXISTS app_config (
    config_key   VARCHAR(64) PRIMARY KEY,
    config_value VARCHAR(256) NOT NULL DEFAULT '',
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE app_config IS '앱 공통 설정 (min_app_version 등). 배포 없이 변경 가능';
COMMENT ON COLUMN app_config.config_key IS '설정 키 (예: min_app_version, min_android_version)';
COMMENT ON COLUMN app_config.config_value IS '설정값';

-- 기본 행: 최소 앱 버전 (현재 앱 버전 1.1.8 기준). 빈 값이면 프론트/서버 env fallback 사용.
INSERT INTO app_config (config_key, config_value)
VALUES ('min_app_version', '1.1.8')
ON CONFLICT (config_key) DO NOTHING;
