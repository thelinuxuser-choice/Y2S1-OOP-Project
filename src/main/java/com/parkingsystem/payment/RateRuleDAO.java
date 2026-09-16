package com.parkingsystem.payment;

import com.parkingsystem.common.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RateRuleDAO {

    public List<Map<String, Object>> listActive() {
        String sql = "SELECT rule_id, rule_name, strategy_key, multiplier, is_active FROM rate_rules ORDER BY rule_id";
        return query(sql);
    }

    /** Active rule multiplier, or null if none. */
    public BigDecimal activeMultiplier(String strategyKey) {
        String sql = "SELECT multiplier FROM rate_rules WHERE strategy_key = ? AND is_active = 1 LIMIT 1";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, strategyKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("multiplier");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("rate rule lookup failed", e);
        }
        return null;
    }

    public BigDecimal multiplierFor(String strategyKey, BigDecimal def) {
        BigDecimal m = activeMultiplier(strategyKey);
        return m != null ? m : def;
    }

    public Map<String, Object> create(String name, String key, BigDecimal multiplier) {
        String normKey = StrategyKeyDAO.normalize(key);
        new StrategyKeyDAO().ensureKey(normKey, name, "SLOT");
        String sql = "INSERT INTO rate_rules (rule_name, strategy_key, multiplier, is_active) VALUES (?, ?, ?, 1)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, normKey);
            ps.setBigDecimal(3, multiplier);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                Map<String, Object> m = new HashMap<>();
                m.put("ruleId", keys.getInt(1));
                m.put("ruleName", name);
                m.put("strategyKey", normKey);
                m.put("multiplier", multiplier);
                m.put("active", true);
                return m;
            }
        } catch (Exception e) {
            throw new RuntimeException("create rate rule failed", e);
        }
    }

    public boolean update(int ruleId, BigDecimal multiplier, Boolean active) {
        String sql = "UPDATE rate_rules SET multiplier = COALESCE(?, multiplier), "
                + "is_active = COALESCE(?, is_active) WHERE rule_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (multiplier != null) {
                ps.setBigDecimal(1, multiplier);
            } else {
                ps.setObject(1, null);
            }
            if (active != null) {
                ps.setInt(2, active ? 1 : 0);
            } else {
                ps.setObject(2, null);
            }
            ps.setInt(3, ruleId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            throw new RuntimeException("update rate rule failed", e);
        }
    }

    public boolean delete(int ruleId) {
        String sql = "DELETE FROM rate_rules WHERE rule_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, ruleId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            throw new RuntimeException("delete rate rule failed", e);
        }
    }

    private List<Map<String, Object>> query(String sql) {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("ruleId", rs.getInt("rule_id"));
                m.put("ruleName", rs.getString("rule_name"));
                m.put("strategyKey", rs.getString("strategy_key"));
                m.put("multiplier", rs.getBigDecimal("multiplier"));
                m.put("active", rs.getBoolean("is_active"));
                list.add(m);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return list;
    }
}
