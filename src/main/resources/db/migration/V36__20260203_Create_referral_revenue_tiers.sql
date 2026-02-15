CREATE TABLE IF NOT EXISTS referral_revenue_tiers (
    id BIGSERIAL PRIMARY KEY,
    min_team_size INT NOT NULL,
    max_team_size INT,
    revenue_percent NUMERIC(5, 2) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO referral_revenue_tiers (min_team_size, max_team_size, revenue_percent, is_active, sort_order)
SELECT *
FROM (
    VALUES
        (1, 4, 3.00, TRUE, 1),
        (5, 9, 5.00, TRUE, 2),
        (10, 19, 7.00, TRUE, 3),
        (20, 49, 9.00, TRUE, 4),
        (50, 99, 11.00, TRUE, 5),
        (100, NULL, 13.00, TRUE, 6)
) AS v(min_team_size, max_team_size, revenue_percent, is_active, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM referral_revenue_tiers);
