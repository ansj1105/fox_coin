-- Apple은 버전 정책이 다르므로 별도 설정값 컬럼 추가
ALTER TABLE app_config ADD COLUMN IF NOT EXISTS config_value_apple VARCHAR(256) NOT NULL DEFAULT '';
COMMENT ON COLUMN app_config.config_value_apple IS 'Apple(iOS) 전용 설정값. 비어 있으면 config_value 사용';
