-- 테스트용 API Key 데이터
-- R__로 시작하는 파일은 Repeatable migration으로, 내용이 변경되면 다시 실행됩니다

-- 기존 테스트 데이터 삭제
DELETE FROM api_keys WHERE client_name LIKE 'Test Client%';

-- 테스트용 API Key 추가
INSERT INTO api_keys (api_key, api_secret, client_name, description, is_active, expires_at, created_at, updated_at)
VALUES 
    -- 활성화된 테스트 API Key (만료일 없음)
    ('test_api_key_001', 'test_secret_001', 'Test Client 1', '테스트용 클라이언트 1', true, NULL, NOW(), NOW()),
    -- 활성화된 테스트 API Key (만료일 있음, 미래)
    ('test_api_key_002', 'test_secret_002', 'Test Client 2', '테스트용 클라이언트 2 (만료일 있음)', true, NOW() + INTERVAL '30 days', NOW(), NOW()),
    -- 비활성화된 테스트 API Key
    ('test_api_key_003', 'test_secret_003', 'Test Client 3', '테스트용 클라이언트 3 (비활성화)', false, NULL, NOW(), NOW()),
    -- 만료된 테스트 API Key
    ('test_api_key_004', 'test_secret_004', 'Test Client 4', '테스트용 클라이언트 4 (만료됨)', true, NOW() - INTERVAL '1 day', NOW(), NOW())
ON CONFLICT (api_key) DO UPDATE
SET 
    api_secret = EXCLUDED.api_secret,
    client_name = EXCLUDED.client_name,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active,
    expires_at = EXCLUDED.expires_at,
    updated_at = NOW();

-- 시퀀스 리셋 (ID 순서 보장)
SELECT setval('api_keys_id_seq', (SELECT COALESCE(MAX(id), 1) FROM api_keys));

