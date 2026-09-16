-- Apply on existing DBs that already ran schema.sql (safe to re-run carefully)
USE parking_system;

ALTER TABLE inquiries
  MODIFY status ENUM('OPEN','IN_PROGRESS','ESCALATED','RESOLVED','CLOSED')
  NOT NULL DEFAULT 'OPEN';

CREATE TABLE IF NOT EXISTS loyalty_config (
    config_key   VARCHAR(40) PRIMARY KEY,
    config_value VARCHAR(80) NOT NULL
);

-- Strategy keys, slot rate_strategy_key, feedback edit history: see patch_strategy_keys_feedback.sql

INSERT IGNORE INTO loyalty_config (config_key, config_value) VALUES
('earn_lkr_per_point', '50'),
('redeem_points_per_lkr', '10'),
('min_redeem_points', '20'),
('tier_silver', '100'),
('tier_gold', '500'),
('tier_platinum', '1000');
