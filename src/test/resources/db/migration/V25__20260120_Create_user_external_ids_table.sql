-- 사용자 외부 서비스 ID 매핑 테이블
-- Create User External IDs Table
CREATE TABLE user_external_ids (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    provider VARCHAR(50) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_user_external_ids PRIMARY KEY (id),
    CONSTRAINT FK_user_external_ids_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT UK_user_external_ids_provider_external_id UNIQUE (provider, external_id),
    CONSTRAINT UK_user_external_ids_user_provider UNIQUE (user_id, provider)
);

COMMENT ON TABLE user_external_ids IS '사용자 외부 서비스 ID 매핑 테이블';
COMMENT ON COLUMN user_external_ids.id IS 'Sequence ID';
COMMENT ON COLUMN user_external_ids.user_id IS '내부 사용자 ID';
COMMENT ON COLUMN user_external_ids.provider IS '외부 서비스 구분자';
COMMENT ON COLUMN user_external_ids.external_id IS '외부 서비스 사용자 식별자';
COMMENT ON COLUMN user_external_ids.created_at IS '생성 시간';
COMMENT ON COLUMN user_external_ids.updated_at IS '수정 시간';

-- 인덱스 생성
CREATE INDEX IDX_user_external_ids_user_id ON user_external_ids(user_id);
