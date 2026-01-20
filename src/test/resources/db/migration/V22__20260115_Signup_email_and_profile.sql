-- 회원가입용 이메일 인증 코드 (비인증, user_id 없음)
CREATE TABLE IF NOT EXISTS signup_email_codes (
    id BIGSERIAL NOT NULL,
    email VARCHAR(255) NOT NULL,
    code VARCHAR(10) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_signup_email_codes PRIMARY KEY (id),
    CONSTRAINT UK_signup_email_codes_email UNIQUE (email)
);

COMMENT ON TABLE signup_email_codes IS '회원가입용 이메일 인증 코드 (send-code → verify → register)';

CREATE INDEX IF NOT EXISTS IDX_signup_email_codes_email ON signup_email_codes(email);

-- users 프로필 필드 추가 (nickname: 한글·영문·숫자 8자리, name: 실제 사용자 이름)
ALTER TABLE users ADD COLUMN IF NOT EXISTS nickname VARCHAR(8) NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS name VARCHAR(50) NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS gender VARCHAR(1) NULL;

CREATE UNIQUE INDEX IF NOT EXISTS UK_users_nickname ON users(nickname) WHERE nickname IS NOT NULL;

CREATE TRIGGER update_signup_email_codes_updated_at
    BEFORE UPDATE ON signup_email_codes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
