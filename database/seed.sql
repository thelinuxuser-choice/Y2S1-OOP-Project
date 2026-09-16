USE parking_system;

-- password for all demos: Password@123 (SHA-256 hex)
INSERT INTO users (full_name, email, password_hash, phone, role) VALUES
('Demo Customer', 'customer@park.lk', 'ff7bd97b1a7789ddd2775122fd6817f3173672da9f802ceec57f284325bf589f', '0771111111', 'CUSTOMER'),
('Gate Attendant', 'attendant@park.lk', 'ff7bd97b1a7789ddd2775122fd6817f3173672da9f802ceec57f284325bf589f', '0772222222', 'ATTENDANT'),
('Facility Manager', 'manager@park.lk', 'ff7bd97b1a7789ddd2775122fd6817f3173672da9f802ceec57f284325bf589f', '0773333333', 'MANAGER'),
('System Admin', 'admin@park.lk', 'ff7bd97b1a7789ddd2775122fd6817f3173672da9f802ceec57f284325bf589f', '0774444444', 'ADMIN');

INSERT INTO vehicles (user_id, plate_number, vehicle_type) VALUES
(1, 'CAB-1234', 'CAR'),
(1, 'EV-8899', 'EV');

INSERT INTO facilities (name, location) VALUES
('SLIIT Malabe Parking', 'New Kandy Road, Malabe');

INSERT INTO floors (facility_id, floor_label) VALUES
(1, 'Ground'),
(1, 'Level 1');

INSERT INTO slots (floor_id, slot_code, zone_label, slot_type, status, base_rate, pos_row, pos_col) VALUES
(1, 'G-A1', 'Zone A', 'STANDARD', 'AVAILABLE', 100.00, 0, 0),
(1, 'G-A2', 'Zone A', 'STANDARD', 'RESERVED', 100.00, 0, 1),
(1, 'G-A3', 'Zone A', 'EV', 'AVAILABLE', 150.00, 0, 2),
(1, 'G-B1', 'Zone B', 'ACCESSIBLE', 'OCCUPIED', 80.00, 1, 0),
(1, 'G-B2', 'Zone B', 'COVERED', 'AVAILABLE', 120.00, 1, 1),
(1, 'G-B3', 'Zone B', 'STANDARD', 'MAINTENANCE', 100.00, 1, 2),
(2, 'L1-A1', 'Zone A', 'STANDARD', 'AVAILABLE', 110.00, 0, 0),
(2, 'L1-A2', 'Zone A', 'EV', 'RESERVED', 160.00, 0, 1),
(2, 'L1-A3', 'Zone A', 'STANDARD', 'AVAILABLE', 110.00, 0, 2),
(2, 'L1-B1', 'Zone B', 'COVERED', 'OCCUPIED', 130.00, 1, 0),
(2, 'L1-B2', 'Zone B', 'STANDARD', 'AVAILABLE', 110.00, 1, 1),
(2, 'L1-B3', 'Zone B', 'ACCESSIBLE', 'AVAILABLE', 90.00, 1, 2);

INSERT INTO pricing_strategy_keys (key_code, label, key_scope) VALUES
('PEAK_HOUR', 'Peak hours (time-based)', 'GLOBAL'),
('EV_SLOT', 'EV bay surcharge', 'SLOT'),
('ACCESSIBLE', 'Accessible bay discount', 'SLOT'),
('COVERED', 'Covered bay surcharge', 'SLOT');

INSERT INTO rate_rules (rule_name, strategy_key, multiplier, is_active) VALUES
('Peak morning/evening', 'PEAK_HOUR', 1.50, 1),
('EV charging bay surcharge', 'EV_SLOT', 1.25, 1),
('Accessible flat discount', 'ACCESSIBLE', 0.80, 1),
('Covered bay surcharge', 'COVERED', 1.15, 1);

INSERT INTO loyalty_accounts (user_id, points_balance, tier) VALUES (1, 120, 'SILVER');

INSERT INTO loyalty_config (config_key, config_value) VALUES
('earn_lkr_per_point', '50'),
('redeem_points_per_lkr', '10'),
('min_redeem_points', '20'),
('tier_silver', '100'),
('tier_gold', '500'),
('tier_platinum', '1000');
