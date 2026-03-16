-- Swap pricing and policy defaults (editable in DB without redeploy)
INSERT INTO app_config (config_key, config_value)
VALUES
    ('swap.fee_bps', '20'),
    ('swap.spread_bps', '30'),
    ('swap.min_amount.default', '0.000001'),
    ('swap.min_amount.KORI', '1000'),
    ('swap.min_amount.KRWT', '1000'),
    ('swap.price_source', 'DB_MARKET'),
    ('swap.price_note', 'Realtime price source: DB market price')
ON CONFLICT (config_key) DO NOTHING;
