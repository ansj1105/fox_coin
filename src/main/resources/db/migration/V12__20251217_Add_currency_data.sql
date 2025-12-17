-- 통화 데이터 추가 (프로덕션용)
-- TRX, USDT, BTC, ETH, KORI 통화 추가

-- TRON 체인 통화
INSERT INTO currency (code, name, chain, is_active, created_at, updated_at)
VALUES 
    ('TRX', 'TRON', 'TRON', true, NOW(), NOW()),
    ('USDT', 'Tether USD', 'TRON', true, NOW(), NOW()),
    ('KORI', 'KORI Token', 'TRON', true, NOW(), NOW())
ON CONFLICT (code, chain) DO UPDATE
SET 
    name = EXCLUDED.name,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();

-- ETH 체인 통화
INSERT INTO currency (code, name, chain, is_active, created_at, updated_at)
VALUES 
    ('ETH', 'Ethereum', 'ETH', true, NOW(), NOW()),
    ('ETH', 'Ethereum', 'Ether', true, NOW(), NOW())  -- 호환성을 위해 둘 다 추가
ON CONFLICT (code, chain) DO UPDATE
SET 
    name = EXCLUDED.name,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();

-- BTC 체인 통화
INSERT INTO currency (code, name, chain, is_active, created_at, updated_at)
VALUES 
    ('BTC', 'Bitcoin', 'BTC', true, NOW(), NOW())
ON CONFLICT (code, chain) DO UPDATE
SET 
    name = EXCLUDED.name,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();

COMMENT ON TABLE currency IS '통화 종류 테이블 - TRX, USDT, BTC, ETH, KORI 추가됨';

