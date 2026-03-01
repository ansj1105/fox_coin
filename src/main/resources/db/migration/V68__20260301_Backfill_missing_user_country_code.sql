-- Backfill missing user country codes to ETC for global country-code rollout.
UPDATE users
SET country_code = 'ETC'
WHERE country_code IS NULL
   OR BTRIM(country_code) = '';
