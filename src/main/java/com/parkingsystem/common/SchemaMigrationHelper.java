package com.parkingsystem.common;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Applies small DDL deltas on first use so older local DBs keep working without a manual patch.
 */
public final class SchemaMigrationHelper {

    private static volatile boolean applied;

    private SchemaMigrationHelper() {
    }

    public static void ensureRuntimeSchema() {
        if (applied) {
            return;
        }
        synchronized (SchemaMigrationHelper.class) {
            if (applied) {
                return;
            }
            try (Connection c = DBConnection.getConnection();
                 Statement st = c.createStatement()) {
                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS pricing_strategy_keys ("
                                + "key_code VARCHAR(40) PRIMARY KEY, "
                                + "label VARCHAR(80) NOT NULL, "
                                + "key_scope ENUM('SLOT','GLOBAL') NOT NULL DEFAULT 'SLOT', "
                                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
                st.executeUpdate(
                        "INSERT IGNORE INTO pricing_strategy_keys (key_code, label, key_scope) VALUES "
                                + "('PEAK_HOUR','Peak hours (time-based)','GLOBAL'),"
                                + "('EV_SLOT','EV bay surcharge','SLOT'),"
                                + "('ACCESSIBLE','Accessible bay discount','SLOT'),"
                                + "('COVERED','Covered bay surcharge','SLOT')");
                addColumnIfMissing(st, "slots", "rate_strategy_key",
                        "ALTER TABLE slots ADD COLUMN rate_strategy_key VARCHAR(40) NULL AFTER pos_col");
                addColumnIfMissing(st, "feedback", "original_rating",
                        "ALTER TABLE feedback ADD COLUMN original_rating TINYINT NULL AFTER feedback_type");
                addColumnIfMissing(st, "feedback", "original_comment_text",
                        "ALTER TABLE feedback ADD COLUMN original_comment_text VARCHAR(1000) NULL AFTER original_rating");
                addColumnIfMissing(st, "feedback", "edited_at",
                        "ALTER TABLE feedback ADD COLUMN edited_at DATETIME NULL AFTER original_comment_text");
                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS loyalty_config ("
                                + "config_key VARCHAR(40) PRIMARY KEY, "
                                + "config_value VARCHAR(80) NOT NULL)");
                st.executeUpdate(
                        "INSERT IGNORE INTO loyalty_config (config_key, config_value) VALUES "
                                + "('earn_lkr_per_point','50'),"
                                + "('redeem_points_per_lkr','10'),"
                                + "('min_redeem_points','20'),"
                                + "('tier_silver','100'),"
                                + "('tier_gold','500'),"
                                + "('tier_platinum','1000')");
            } catch (Exception e) {
                throw new RuntimeException(
                        "Database schema update failed — run database/patch_strategy_keys_feedback.sql: "
                                + e.getMessage(), e);
            }
            applied = true;
        }
    }

    private static void addColumnIfMissing(Statement st, String table, String column, String alterSql)
            throws SQLException {
        try {
            st.executeUpdate(alterSql);
        } catch (SQLException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("duplicate column") || msg.contains("already exists")) {
                return;
            }
            throw e;
        }
    }
}
