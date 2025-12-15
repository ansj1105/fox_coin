-- 이메일 인증 테이블 생성

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

COMMENT ON TABLE email_verifications IS '이메일 인증 테이블';
COMMENT ON COLUMN email_verifications.id IS 'Sequence ID';
COMMENT ON COLUMN email_verifications.user_id IS '사용자 ID';
COMMENT ON COLUMN email_verifications.email IS '이메일 주소';
COMMENT ON COLUMN email_verifications.verification_code IS '인증 코드';
COMMENT ON COLUMN email_verifications.is_verified IS '인증 여부';
COMMENT ON COLUMN email_verifications.verified_at IS '인증 완료 시각';
COMMENT ON COLUMN email_verifications.expires_at IS '인증 코드 만료 시각';

-- 거래 비밀번호 컬럼 추가 (해시 저장)
ALTER TABLE users ADD COLUMN IF NOT EXISTS transaction_password_hash VARCHAR(255);
COMMENT ON COLUMN users.transaction_password_hash IS '거래 비밀번호 해시 (6자리 숫자, BCrypt 해시로 저장)';


