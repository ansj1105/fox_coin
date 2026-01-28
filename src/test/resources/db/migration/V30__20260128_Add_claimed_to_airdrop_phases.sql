-- 에어드랍 Phase: 보상형 영상 시청 후 Release(락업 해제) 완료 여부
ALTER TABLE airdrop_phases ADD COLUMN IF NOT EXISTS claimed BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN airdrop_phases.claimed IS 'true: 보상형 영상 시청 후 Release 완료, 락업 해제 금액에 반영됨. false: Release 버튼으로 해제 가능';
