-- Mining partner coefficients for partner-provided mining activation.
CREATE TABLE IF NOT EXISTS mining_boosters (
    id SERIAL NOT NULL,
    type VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT true,
    efficiency INT NULL,
    max_count INT NULL,
    per_unit_efficiency INT NULL,
    note TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_mining_boosters PRIMARY KEY (id),
    CONSTRAINT UK_mining_boosters_type UNIQUE (type)
);

INSERT INTO mining_boosters (type, name, is_enabled, efficiency, max_count, per_unit_efficiency, note, updated_at)
VALUES (
    'PARTNER_FOXYYA',
    'Foxyya 채굴 활성화 효율',
    true,
    0,
    NULL,
    NULL,
    'Foxyya 채굴 활성화 시 곱하는 파트너 변동 계수입니다. efficiency 10 = x1.10, 0 = x1.00',
    NOW()
)
ON CONFLICT (type) DO UPDATE SET
    name = EXCLUDED.name,
    is_enabled = COALESCE(mining_boosters.is_enabled, EXCLUDED.is_enabled),
    efficiency = COALESCE(mining_boosters.efficiency, EXCLUDED.efficiency),
    note = EXCLUDED.note,
    updated_at = NOW();
