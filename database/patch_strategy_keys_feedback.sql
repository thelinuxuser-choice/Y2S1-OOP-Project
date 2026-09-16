-- Run on existing parking_system DB (after schema.sql)
USE parking_system;

CREATE TABLE IF NOT EXISTS pricing_strategy_keys (
    key_code   VARCHAR(40) PRIMARY KEY,
    label      VARCHAR(80) NOT NULL,
    key_scope  ENUM('SLOT','GLOBAL') NOT NULL DEFAULT 'SLOT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT IGNORE INTO pricing_strategy_keys (key_code, label, key_scope) VALUES
('PEAK_HOUR', 'Peak hours (time-based)', 'GLOBAL'),
('EV_SLOT', 'EV bay surcharge', 'SLOT'),
('ACCESSIBLE', 'Accessible bay discount', 'SLOT'),
('COVERED', 'Covered bay surcharge', 'SLOT');

SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slots' AND COLUMN_NAME = 'rate_strategy_key'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE slots ADD COLUMN rate_strategy_key VARCHAR(40) NULL AFTER pos_col',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fb_orig = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedback' AND COLUMN_NAME = 'original_rating'
);
SET @sql2 = IF(@fb_orig = 0,
  'ALTER TABLE feedback ADD COLUMN original_rating TINYINT NULL AFTER feedback_type, '
  'ADD COLUMN original_comment_text VARCHAR(1000) NULL AFTER original_rating, '
  'ADD COLUMN edited_at DATETIME NULL AFTER original_comment_text',
  'SELECT 1');
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
