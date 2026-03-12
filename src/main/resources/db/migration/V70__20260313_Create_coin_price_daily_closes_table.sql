CREATE TABLE IF NOT EXISTS coin_price_daily_closes (
    currency_code VARCHAR(10) NOT NULL,
    close_date DATE NOT NULL,
    usd_close_price NUMERIC(30, 10) NOT NULL,
    source_price_updated_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (currency_code, close_date)
);

CREATE INDEX IF NOT EXISTS idx_coin_price_daily_closes_close_date
    ON coin_price_daily_closes (close_date);
