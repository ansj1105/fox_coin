ALTER TABLE offline_pay_trust_center_snapshots
    ADD COLUMN IF NOT EXISTS key_provider VARCHAR(64) NOT NULL DEFAULT '';

ALTER TABLE offline_pay_trust_center_snapshots
    ADD COLUMN IF NOT EXISTS hardware_backed_key BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE offline_pay_trust_center_snapshots
    ADD COLUMN IF NOT EXISTS user_presence_protected BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE offline_pay_trust_center_snapshots
    ADD COLUMN IF NOT EXISTS secure_hardware_level VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE offline_pay_trust_center_snapshots
    ADD COLUMN IF NOT EXISTS attestation_class VARCHAR(64) NOT NULL DEFAULT 'UNAVAILABLE';

ALTER TABLE offline_pay_trust_center_snapshots
    ADD COLUMN IF NOT EXISTS attestation_verdict VARCHAR(64) NOT NULL DEFAULT 'MIRROR_ONLY';

ALTER TABLE offline_pay_trust_center_snapshots
    ADD COLUMN IF NOT EXISTS server_verified_trust_level VARCHAR(64) NOT NULL DEFAULT 'LOCAL_ONLY';
