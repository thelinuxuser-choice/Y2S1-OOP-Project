-- Y2S1-MLB-B10G1-07
-- Automated Parking Reservation & Management System
-- Run this against MySQL before deploying the WAR

CREATE DATABASE IF NOT EXISTS parking_system
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE parking_system;

-- ---------- users & auth ----------
CREATE TABLE users (
    user_id       INT AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(120) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    phone         VARCHAR(20),
    role          ENUM('CUSTOMER','ATTENDANT','MANAGER','ADMIN') NOT NULL DEFAULT 'CUSTOMER',
    is_active     TINYINT(1) NOT NULL DEFAULT 1,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE vehicles (
    vehicle_id   INT AUTO_INCREMENT PRIMARY KEY,
    user_id      INT NOT NULL,
    plate_number VARCHAR(20) NOT NULL,
    vehicle_type ENUM('CAR','VAN','MOTORCYCLE','EV') NOT NULL DEFAULT 'CAR',
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- ---------- facility layout (feeds live map + pricing) ----------
CREATE TABLE facilities (
    facility_id INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    location    VARCHAR(200) NOT NULL,
    is_active   TINYINT(1) NOT NULL DEFAULT 1
);

CREATE TABLE floors (
    floor_id    INT AUTO_INCREMENT PRIMARY KEY,
    facility_id INT NOT NULL,
    floor_label VARCHAR(40) NOT NULL,
    FOREIGN KEY (facility_id) REFERENCES facilities(facility_id) ON DELETE CASCADE
);

CREATE TABLE slots (
    slot_id     INT AUTO_INCREMENT PRIMARY KEY,
    floor_id    INT NOT NULL,
    slot_code   VARCHAR(20) NOT NULL,
    zone_label  VARCHAR(40),
    slot_type   ENUM('STANDARD','EV','ACCESSIBLE','COVERED') NOT NULL DEFAULT 'STANDARD',
    status      ENUM('AVAILABLE','RESERVED','OCCUPIED','MAINTENANCE') NOT NULL DEFAULT 'AVAILABLE',
    base_rate   DECIMAL(10,2) NOT NULL DEFAULT 100.00,
    pos_row     INT DEFAULT 0,
    pos_col     INT DEFAULT 0,
    rate_strategy_key VARCHAR(40) NULL,
    UNIQUE KEY uq_floor_slot (floor_id, slot_code),
    FOREIGN KEY (floor_id) REFERENCES floors(floor_id) ON DELETE CASCADE
);

-- ---------- reservation (Kumarathunga) ----------
CREATE TABLE reservations (
    reservation_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id        INT NOT NULL,
    slot_id        INT NOT NULL,
    vehicle_id     INT,
    start_time     DATETIME NOT NULL,
    end_time       DATETIME NOT NULL,
    status         ENUM('PENDING','ACTIVE','COMPLETED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    lock_until     DATETIME NULL,
    confirmation_token VARCHAR(64),
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (slot_id) REFERENCES slots(slot_id),
    FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id)
);

-- ---------- payment (Gunathilaka) ----------
CREATE TABLE payments (
    payment_id     INT AUTO_INCREMENT PRIMARY KEY,
    reservation_id INT NOT NULL,
    base_amount    DECIMAL(10,2) NOT NULL,
    discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    final_amount   DECIMAL(10,2) NOT NULL,
    rate_strategy  VARCHAR(80),
    status         ENUM('PENDING','PAID','REFUNDED','FAILED') NOT NULL DEFAULT 'PENDING',
    invoice_no     VARCHAR(40),
    paid_at        DATETIME NULL,
    FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id)
);

CREATE TABLE pricing_strategy_keys (
    key_code   VARCHAR(40) PRIMARY KEY,
    label      VARCHAR(80) NOT NULL,
    key_scope  ENUM('SLOT','GLOBAL') NOT NULL DEFAULT 'SLOT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rate_rules (
    rule_id     INT AUTO_INCREMENT PRIMARY KEY,
    rule_name   VARCHAR(80) NOT NULL,
    strategy_key VARCHAR(40) NOT NULL,
    multiplier  DECIMAL(5,2) NOT NULL DEFAULT 1.00,
    is_active   TINYINT(1) NOT NULL DEFAULT 1
);

-- ---------- loyalty (Jayasundera) ----------
CREATE TABLE loyalty_accounts (
    loyalty_id   INT AUTO_INCREMENT PRIMARY KEY,
    user_id      INT NOT NULL UNIQUE,
    points_balance INT NOT NULL DEFAULT 0,
    tier         ENUM('BRONZE','SILVER','GOLD','PLATINUM') NOT NULL DEFAULT 'BRONZE',
    enrolled_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE loyalty_transactions (
    txn_id      INT AUTO_INCREMENT PRIMARY KEY,
    loyalty_id  INT NOT NULL,
    points      INT NOT NULL,
    txn_type    ENUM('EARN','REDEEM','ADJUST') NOT NULL,
    reference   VARCHAR(80),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (loyalty_id) REFERENCES loyalty_accounts(loyalty_id)
);

-- ---------- feedback (Dilukshitha) ----------
CREATE TABLE feedback (
    feedback_id    INT AUTO_INCREMENT PRIMARY KEY,
    reservation_id INT NOT NULL UNIQUE,
    user_id        INT NOT NULL,
    facility_id    INT NOT NULL,
    rating         TINYINT NOT NULL,
    comment_text   VARCHAR(1000),
    feedback_type  ENUM('RATING_ONLY','RATING_WITH_COMMENT','FACILITY_REVIEW') NOT NULL,
    original_rating TINYINT NULL,
    original_comment_text VARCHAR(1000) NULL,
    edited_at      DATETIME NULL,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CHECK (rating BETWEEN 1 AND 5),
    FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (facility_id) REFERENCES facilities(facility_id)
);

-- ---------- inquiries (Samarasinghe) ----------
CREATE TABLE inquiries (
    inquiry_id   INT AUTO_INCREMENT PRIMARY KEY,
    reference_no VARCHAR(30) NOT NULL UNIQUE,
    user_id      INT NOT NULL,
    category     ENUM('BOOKING','PAYMENT','LOYALTY','MAP_SLOT','GENERAL') NOT NULL,
    subject      VARCHAR(150) NOT NULL,
    description  TEXT NOT NULL,
    related_ref  VARCHAR(80),
    status       ENUM('OPEN','IN_PROGRESS','ESCALATED','RESOLVED','CLOSED') NOT NULL DEFAULT 'OPEN',
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE inquiry_replies (
    reply_id    INT AUTO_INCREMENT PRIMARY KEY,
    inquiry_id  INT NOT NULL,
    staff_id    INT NOT NULL,
    message     TEXT NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (inquiry_id) REFERENCES inquiries(inquiry_id) ON DELETE CASCADE,
    FOREIGN KEY (staff_id) REFERENCES users(user_id)
);

-- ---------- minor: audit / notifications ----------
CREATE TABLE audit_logs (
    log_id     INT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT,
    action     VARCHAR(120) NOT NULL,
    details    VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notifications (
    notification_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT NOT NULL,
    title      VARCHAR(120) NOT NULL,
    body       VARCHAR(500),
    is_read    TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- loyalty rule knobs (Singleton manager reads these)
CREATE TABLE loyalty_config (
    config_key   VARCHAR(40) PRIMARY KEY,
    config_value VARCHAR(80) NOT NULL
);
