-- Add deposit dedupe fields and chain cursor table
ALTER TABLE token_deposits ADD COLUMN IF NOT EXISTS to_address VARCHAR(255) NULL;
COMMENT ON COLUMN token_deposits.to_address IS '수신 지갑 주소';

ALTER TABLE token_deposits ADD COLUMN IF NOT EXISTS log_index INT NULL;
COMMENT ON COLUMN token_deposits.log_index IS '이벤트 로그 인덱스 (EVM 등)';

ALTER TABLE token_deposits ADD COLUMN IF NOT EXISTS block_number BIGINT NULL;
COMMENT ON COLUMN token_deposits.block_number IS '블록 번호';

CREATE INDEX IF NOT EXISTS idx_token_deposits_to_address ON token_deposits(to_address);
CREATE INDEX IF NOT EXISTS idx_token_deposits_block_number ON token_deposits(block_number);

CREATE UNIQUE INDEX IF NOT EXISTS uk_token_deposits_tx_unique
    ON token_deposits (network, tx_hash, COALESCE(log_index, -1), COALESCE(to_address, ''));

CREATE TABLE IF NOT EXISTS chain_block_cursor (
    id BIGSERIAL PRIMARY KEY,
    chain VARCHAR(32) NOT NULL UNIQUE,
    last_scanned_block BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE chain_block_cursor IS '체인별 블록 스캔 커서';
COMMENT ON COLUMN chain_block_cursor.chain IS '체인 이름';
COMMENT ON COLUMN chain_block_cursor.last_scanned_block IS '마지막 스캔 블록 번호';
COMMENT ON COLUMN chain_block_cursor.updated_at IS '업데이트 시간';
