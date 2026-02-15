-- 내부 적립용 KORI INTERNAL 통화 (채굴·래퍼럴·에어드랍 등 DB 잔액만 사용)
INSERT INTO currency (code, name, chain, is_active, created_at, updated_at)
VALUES ('KORI', 'KORI (Internal)', 'INTERNAL', true, NOW(), NOW())
ON CONFLICT (code, chain) DO UPDATE
SET name = EXCLUDED.name, is_active = EXCLUDED.is_active, updated_at = NOW();
