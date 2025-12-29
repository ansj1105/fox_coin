-- 문의하기 테이블
-- Create Inquiries Table
CREATE TABLE inquiries (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    subject VARCHAR(20) NOT NULL,
    content VARCHAR(200) NOT NULL,
    email VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_inquiries PRIMARY KEY (id),
    CONSTRAINT FK_inquiries_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

COMMENT ON TABLE inquiries IS '문의하기 테이블';
COMMENT ON COLUMN inquiries.id IS 'Sequence ID';
COMMENT ON COLUMN inquiries.user_id IS '유저 ID';
COMMENT ON COLUMN inquiries.subject IS '문의 제목 (최대 20자)';
COMMENT ON COLUMN inquiries.content IS '문의 내용 (최대 200자)';
COMMENT ON COLUMN inquiries.email IS '사용자 이메일 주소';
COMMENT ON COLUMN inquiries.status IS '문의 상태 (PENDING, PROCESSING, COMPLETED)';
COMMENT ON COLUMN inquiries.created_at IS '생성 시간';
COMMENT ON COLUMN inquiries.updated_at IS '수정 시간';

-- 인덱스 생성
CREATE INDEX idx_inquiries_user_id ON inquiries(user_id);
CREATE INDEX idx_inquiries_status ON inquiries(status);
CREATE INDEX idx_inquiries_created_at ON inquiries(created_at DESC);

