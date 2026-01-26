-- Add F_COIN currency for exchange
INSERT INTO currency (code, name, chain, is_active, created_at, updated_at)
VALUES ('F_COIN', 'Foxya Coin', 'INTERNAL', true, NOW(), NOW())
ON CONFLICT (code, chain) DO NOTHING;

-- Exchange settings table
CREATE TABLE IF NOT EXISTS exchange_settings (
    id BIGSERIAL PRIMARY KEY,
    from_currency_code VARCHAR(20) NOT NULL,
    to_currency_code VARCHAR(20) NOT NULL,
    exchange_rate NUMERIC(30,10) NOT NULL,
    fee NUMERIC(30,10) NOT NULL,
    min_exchange_amount NUMERIC(30,10) NOT NULL,
    note TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT UK_exchange_settings_pair UNIQUE (from_currency_code, to_currency_code)
);

COMMENT ON TABLE exchange_settings IS '환전 설정 (환율, 수수료, 최소 금액)';
COMMENT ON COLUMN exchange_settings.from_currency_code IS 'FROM 통화 코드';
COMMENT ON COLUMN exchange_settings.to_currency_code IS 'TO 통화 코드';
COMMENT ON COLUMN exchange_settings.exchange_rate IS '환전 비율';
COMMENT ON COLUMN exchange_settings.fee IS '환전 수수료율';
COMMENT ON COLUMN exchange_settings.min_exchange_amount IS '최소 환전 금액';
COMMENT ON COLUMN exchange_settings.note IS '환전 안내 문구';

-- Initial exchange settings
INSERT INTO exchange_settings (
    from_currency_code,
    to_currency_code,
    exchange_rate,
    fee,
    min_exchange_amount,
    note,
    is_active,
    created_at,
    updated_at
) VALUES (
    'KORI',
    'F_COIN',
    21.5,
    0.002,
    10.0,
    '여우야(F코인) → 코인 : 1000:1 비율로 즉시 환전',
    true,
    NOW(),
    NOW()
)
ON CONFLICT (from_currency_code, to_currency_code) DO NOTHING;
