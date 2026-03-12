CREATE TABLE IF NOT EXISTS coin_prices (
    currency_code VARCHAR(10) PRIMARY KEY,
    usd_price NUMERIC(30, 10) NOT NULL,
    change_24h_percent NUMERIC(18, 8),
    source VARCHAR(32) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
