INSERT INTO coin_prices (currency_code, usd_price, change_24h_percent, source, updated_at)
VALUES
    ('BTC', 90000.0, 3.4, 'TEST', NOW()),
    ('ETH', 3200.0, 1.2, 'TEST', NOW()),
    ('KORI', 0.0001132813, 0.0, 'TEST', NOW())
ON CONFLICT (currency_code) DO UPDATE
SET
    usd_price = EXCLUDED.usd_price,
    change_24h_percent = EXCLUDED.change_24h_percent,
    source = EXCLUDED.source,
    updated_at = EXCLUDED.updated_at;
