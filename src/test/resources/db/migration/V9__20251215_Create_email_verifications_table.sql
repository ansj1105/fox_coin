-- 이메일 인증 테이블 생성 (테스트 환경)

CREATE TABLE IF NOT EXISTS email_verifications (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    verification_code VARCHAR(20) NOT NULL,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMP NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_email_verifications PRIMARY KEY (id),
    CONSTRAINT FK_email_verifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT UK_email_verifications_user UNIQUE (user_id)
);

COMMENT ON TABLE email_verifications IS '이메일 인증 테이블 (테스트용)';

-- 거래 비밀번호 컬럼 추가 (해시 저장)
ALTER TABLE users ADD COLUMN IF NOT EXISTS transaction_password_hash VARCHAR(255);

