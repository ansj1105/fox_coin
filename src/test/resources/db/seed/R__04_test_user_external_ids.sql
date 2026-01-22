-- 테스트용 외부 사용자 ID 매핑 데이터
-- R__로 시작하는 파일은 Repeatable migration으로, 내용이 변경되면 다시 실행됩니다

DELETE FROM user_external_ids
WHERE provider = 'test_provider'
  AND external_id IN ('ext_001', 'ext_002');

INSERT INTO user_external_ids (user_id, provider, external_id, created_at, updated_at)
VALUES
  ((SELECT id FROM users WHERE login_id = 'testuser'), 'test_provider', 'ext_001', NOW(), NOW()),
  ((SELECT id FROM users WHERE login_id = 'testuser2'), 'test_provider', 'ext_002', NOW(), NOW())
ON CONFLICT (provider, external_id) DO NOTHING;
