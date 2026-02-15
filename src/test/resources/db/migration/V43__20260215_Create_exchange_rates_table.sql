-- Test migration: exchange_rates table

CREATE TABLE IF NOT EXISTS exchange_rates (
    currency_code VARCHAR(10) PRIMARY KEY,
    krw_rate NUMERIC(30, 10) NOT NULL,
    source VARCHAR(32) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO exchange_rates (currency_code, krw_rate, source)
VALUES
    ('KRWT', 1.0, 'DEFAULT'),
    ('USDT', 1300.0, 'DEFAULT'),
    ('ETH', 5000000.0, 'DEFAULT')
ON CONFLICT (currency_code) DO NOTHING;

