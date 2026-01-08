-- API Key 테이블
-- Create API Keys Table
CREATE TABLE api_keys (
    id BIGSERIAL NOT NULL,
    api_key VARCHAR(255) NOT NULL,
    api_secret VARCHAR(255) NOT NULL,
    client_name VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_api_keys PRIMARY KEY (id),
    CONSTRAINT UK_api_keys_api_key UNIQUE (api_key)
);

COMMENT ON TABLE api_keys IS 'API Key 테이블';
COMMENT ON COLUMN api_keys.id IS 'Sequence ID';
COMMENT ON COLUMN api_keys.api_key IS 'API Key (고유 식별자)';
COMMENT ON COLUMN api_keys.api_secret IS 'API Secret (암호화된 비밀번호)';
COMMENT ON COLUMN api_keys.client_name IS '클라이언트 이름';
COMMENT ON COLUMN api_keys.description IS '설명';
COMMENT ON COLUMN api_keys.is_active IS '활성화 여부';
COMMENT ON COLUMN api_keys.expires_at IS '만료 일시';
COMMENT ON COLUMN api_keys.last_used_at IS '마지막 사용 일시';
COMMENT ON COLUMN api_keys.created_at IS '생성 시간';
COMMENT ON COLUMN api_keys.updated_at IS '수정 시간';

-- 인덱스 생성
CREATE INDEX IDX_api_keys_api_key ON api_keys(api_key);
CREATE INDEX IDX_api_keys_is_active ON api_keys(is_active);
CREATE INDEX IDX_api_keys_expires_at ON api_keys(expires_at);

