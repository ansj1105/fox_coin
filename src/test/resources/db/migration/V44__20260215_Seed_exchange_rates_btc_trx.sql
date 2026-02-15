-- Test DB seed for additional exchange rates.

INSERT INTO exchange_rates (currency_code, krw_rate, source)
VALUES
    ('BTC', 80000000.0, 'DEFAULT'),
    ('TRX', 180.0, 'DEFAULT')
ON CONFLICT (currency_code) DO NOTHING;

