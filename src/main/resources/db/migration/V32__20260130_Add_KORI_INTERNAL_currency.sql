-- 내부 적립용 KORI INTERNAL 통화 (채굴·래퍼럴·에어드랍 등 DB 잔액만 사용, 블록체인 미사용)
-- getCurrencyByCodeAndChainAllowInactive("KORI", "INTERNAL") 로 조회
INSERT INTO currency (code, name, chain, is_active, created_at, updated_at)
VALUES ('KORI', 'KORI (Internal)', 'INTERNAL', true, NOW(), NOW())
ON CONFLICT (code, chain) DO UPDATE
SET name = EXCLUDED.name, is_active = EXCLUDED.is_active, updated_at = NOW();

COMMENT ON TABLE currency IS '통화 - KORI INTERNAL: 내부 적립용(채굴/래퍼럴/에어드랍), TRON: KORI/TRX/USDT 출금용';
