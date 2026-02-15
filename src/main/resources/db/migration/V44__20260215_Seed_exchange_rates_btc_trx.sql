-- Seed additional exchange rates so API can respond even before the first successful refresh.
-- Values are coarse defaults; the scheduler should overwrite them with live rates.

INSERT INTO exchange_rates (currency_code, krw_rate, source)
VALUES
    ('BTC', 80000000.0, 'DEFAULT'),
    ('TRX', 180.0, 'DEFAULT')
ON CONFLICT (currency_code) DO NOTHING;

