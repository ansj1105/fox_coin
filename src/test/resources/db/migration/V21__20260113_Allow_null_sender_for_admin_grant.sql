-- ADMIN_GRANT 타입의 경우 시스템 전송이므로 sender_id와 sender_wallet_id를 NULL 허용으로 변경
-- 기존 외래 키 제약조건 제거 후 NULL 허용으로 변경하고, 조건부 외래 키 제약조건 추가

-- 1. 기존 외래 키 제약조건 제거
ALTER TABLE internal_transfers DROP CONSTRAINT IF EXISTS FK_transfer_sender;
ALTER TABLE internal_transfers DROP CONSTRAINT IF EXISTS FK_transfer_sender_wallet;

-- 2. sender_id와 sender_wallet_id를 NULL 허용으로 변경
ALTER TABLE internal_transfers ALTER COLUMN sender_id DROP NOT NULL;
ALTER TABLE internal_transfers ALTER COLUMN sender_wallet_id DROP NOT NULL;

-- 3. 조건부 외래 키 제약조건 추가 (sender_id가 NULL이 아닐 때만 users 테이블 참조)
-- PostgreSQL에서는 CHECK 제약조건과 함께 사용하거나, 애플리케이션 레벨에서 처리
-- 외래 키는 NULL 값을 허용하므로, sender_id가 NULL이 아닐 때만 참조 무결성 검사
ALTER TABLE internal_transfers 
    ADD CONSTRAINT FK_transfer_sender 
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE RESTRICT;

ALTER TABLE internal_transfers 
    ADD CONSTRAINT FK_transfer_sender_wallet 
    FOREIGN KEY (sender_wallet_id) REFERENCES user_wallets(id) ON DELETE RESTRICT;

COMMENT ON COLUMN internal_transfers.sender_id IS '송신자 유저 ID (NULL 허용: ADMIN_GRANT 타입의 경우 시스템 전송)';
COMMENT ON COLUMN internal_transfers.sender_wallet_id IS '송신자 지갑 ID (NULL 허용: ADMIN_GRANT 타입의 경우 시스템 전송)';

