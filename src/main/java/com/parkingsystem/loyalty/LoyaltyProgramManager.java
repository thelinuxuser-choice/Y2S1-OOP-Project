package com.parkingsystem.loyalty;

import com.parkingsystem.common.AuditLogger;
import com.parkingsystem.common.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// loyalty module – JMSP – one shared manager so points stay consistent
public final class LoyaltyProgramManager {

    private static volatile LoyaltyProgramManager instance;

    private LoyaltyProgramManager() {
    }

    public static LoyaltyProgramManager getInstance() {
        if (instance == null) {
            synchronized (LoyaltyProgramManager.class) {
                if (instance == null) {
                    instance = new LoyaltyProgramManager();
                }
            }
        }
        return instance;
    }

    public Map<String, Object> enroll(int userId) {
        if (getAccount(userId) != null) {
            return getAccount(userId);
        }
        String sql = "INSERT INTO loyalty_accounts (user_id, points_balance, tier) VALUES (?, 0, 'BRONZE')";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("enroll failed", e);
        }
        AuditLogger.log(userId, "LOYALTY_ENROLL", "enrolled");
        return getAccount(userId);
    }

    public Map<String, Object> getAccount(int userId) {
        String sql = "SELECT loyalty_id, user_id, points_balance, tier, enrolled_at FROM loyalty_accounts WHERE user_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("loyaltyId", rs.getInt("loyalty_id"));
                    m.put("userId", rs.getInt("user_id"));
                    m.put("points", rs.getInt("points_balance"));
                    m.put("tier", rs.getString("tier"));
                    m.put("enrolledAt", rs.getTimestamp("enrolled_at").toLocalDateTime().toString());
                    return m;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("getAccount failed", e);
        }
        return null;
    }

    public synchronized void earnPoints(int userId, int points, String reference) {
        Map<String, Object> acc = getAccount(userId);
        if (acc == null) {
            enroll(userId);
            acc = getAccount(userId);
        }
        int loyaltyId = (Integer) acc.get("loyaltyId");
        int newBal = (Integer) acc.get("points") + points;
        updateBalance(loyaltyId, newBal);
        insertTxn(loyaltyId, points, "EARN", reference);
        refreshTier(loyaltyId, newBal);
        AuditLogger.log(userId, "LOYALTY_EARN", points + " pts ref=" + reference);
    }

    public synchronized void redeemPoints(int userId, int points, String reference) {
        Map<String, Object> acc = getAccount(userId);
        if (acc == null) {
            throw new IllegalArgumentException("Not enrolled in loyalty");
        }
        int min = intConfig("min_redeem_points", 20);
        if (points < min) {
            throw new IllegalArgumentException("Minimum redeem is " + min + " points");
        }
        int bal = (Integer) acc.get("points");
        if (points > bal) {
            throw new IllegalArgumentException("Not enough points");
        }
        int loyaltyId = (Integer) acc.get("loyaltyId");
        int newBal = bal - points;
        updateBalance(loyaltyId, newBal);
        insertTxn(loyaltyId, -points, "REDEEM", reference);
        refreshTier(loyaltyId, newBal);
        AuditLogger.log(userId, "LOYALTY_REDEEM", points + " pts ref=" + reference);
    }

    /** Reverse earn/redeem after a payment refund (ADJUST ledger). */
    public synchronized void adjustPoints(int userId, int delta, String reference) {
        Map<String, Object> acc = getAccount(userId);
        if (acc == null) {
            if (delta <= 0) {
                return;
            }
            enroll(userId);
            acc = getAccount(userId);
        }
        int loyaltyId = (Integer) acc.get("loyaltyId");
        int newBal = Math.max(0, (Integer) acc.get("points") + delta);
        updateBalance(loyaltyId, newBal);
        insertTxn(loyaltyId, delta, "ADJUST", reference);
        refreshTier(loyaltyId, newBal);
        AuditLogger.log(userId, "LOYALTY_ADJUST", delta + " pts ref=" + reference);
    }

    public boolean unenroll(int userId) {
        Map<String, Object> acc = getAccount(userId);
        if (acc == null) {
            return false;
        }
        int loyaltyId = (Integer) acc.get("loyaltyId");
        try (Connection c = DBConnection.getConnection()) {
            try (PreparedStatement del = c.prepareStatement(
                    "DELETE FROM loyalty_transactions WHERE loyalty_id = ?")) {
                del.setInt(1, loyaltyId);
                del.executeUpdate();
            }
            try (PreparedStatement del = c.prepareStatement(
                    "DELETE FROM loyalty_accounts WHERE loyalty_id = ?")) {
                del.setInt(1, loyaltyId);
                del.executeUpdate();
            }
        } catch (Exception e) {
            throw new RuntimeException("unenroll failed", e);
        }
        AuditLogger.log(userId, "LOYALTY_UNENROLL", "removed");
        return true;
    }

    public Map<String, String> getRules() {
        Map<String, String> rules = defaultRules();
        String sql = "SELECT config_key, config_value FROM loyalty_config";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rules.put(rs.getString("config_key"), rs.getString("config_value"));
            }
        } catch (Exception ignored) {
            // table may not exist yet on old DBs — fall back to defaults
        }
        return rules;
    }

    public Map<String, String> saveRules(Map<String, String> updates) {
        for (Map.Entry<String, String> e : updates.entrySet()) {
            String sql = "INSERT INTO loyalty_config (config_key, config_value) VALUES (?, ?) "
                    + "ON DUPLICATE KEY UPDATE config_value = VALUES(config_value)";
            try (Connection c = DBConnection.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, e.getKey());
                ps.setString(2, e.getValue());
                ps.executeUpdate();
            } catch (Exception ex) {
                throw new RuntimeException("save loyalty rule failed: " + e.getKey(), ex);
            }
        }
        return getRules();
    }

    public int earnLkrPerPoint() {
        return intConfig("earn_lkr_per_point", 50);
    }

    public int redeemPointsPerLkr() {
        return intConfig("redeem_points_per_lkr", 10);
    }

    private int intConfig(String key, int def) {
        Map<String, String> rules = getRules();
        try {
            return Integer.parseInt(rules.getOrDefault(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private Map<String, String> defaultRules() {
        Map<String, String> m = new HashMap<>();
        m.put("earn_lkr_per_point", "50");
        m.put("redeem_points_per_lkr", "10");
        m.put("min_redeem_points", "20");
        m.put("tier_silver", "100");
        m.put("tier_gold", "500");
        m.put("tier_platinum", "1000");
        return m;
    }

    public List<Map<String, Object>> history(int userId) {
        Map<String, Object> acc = getAccount(userId);
        List<Map<String, Object>> list = new ArrayList<>();
        if (acc == null) {
            return list;
        }
        String sql = "SELECT txn_id, points, txn_type, reference, created_at FROM loyalty_transactions "
                + "WHERE loyalty_id = ? ORDER BY created_at DESC";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, (Integer) acc.get("loyaltyId"));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("txn_id"));
                    row.put("points", rs.getInt("points"));
                    row.put("type", rs.getString("txn_type"));
                    row.put("reference", rs.getString("reference"));
                    row.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
                    list.add(row);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("loyalty history failed", e);
        }
        return list;
    }

    private void updateBalance(int loyaltyId, int bal) {
        String sql = "UPDATE loyalty_accounts SET points_balance = ? WHERE loyalty_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, bal);
            ps.setInt(2, loyaltyId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void insertTxn(int loyaltyId, int points, String type, String ref) {
        String sql = "INSERT INTO loyalty_transactions (loyalty_id, points, txn_type, reference) VALUES (?, ?, ?, ?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, loyaltyId);
            ps.setInt(2, points);
            ps.setString(3, type);
            ps.setString(4, ref);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // thresholds come from loyalty_config when present
    private void refreshTier(int loyaltyId, int bal) {
        Map<String, String> rules = getRules();
        int silver = Integer.parseInt(rules.getOrDefault("tier_silver", "100"));
        int gold = Integer.parseInt(rules.getOrDefault("tier_gold", "500"));
        int platinum = Integer.parseInt(rules.getOrDefault("tier_platinum", "1000"));
        String tier = "BRONZE";
        if (bal >= platinum) {
            tier = "PLATINUM";
        } else if (bal >= gold) {
            tier = "GOLD";
        } else if (bal >= silver) {
            tier = "SILVER";
        }
        String sql = "UPDATE loyalty_accounts SET tier = ? WHERE loyalty_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tier);
            ps.setInt(2, loyaltyId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
