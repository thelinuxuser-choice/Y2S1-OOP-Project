package com.parkingsystem.payment;

import com.parkingsystem.common.DBConnection;
import com.parkingsystem.common.SchemaMigrationHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StrategyKeyDAO {

    public static String normalize(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Key required");
        }
        String k = raw.trim().toUpperCase().replaceAll("[^A-Z0-9_]", "_").replaceAll("_+", "_");
        if (k.isEmpty() || k.length() > 40) {
            throw new IllegalArgumentException("Key must be 1–40 characters (A–Z, 0–9, _)");
        }
        return k;
    }

    public List<Map<String, Object>> listAll() {
        SchemaMigrationHelper.ensureRuntimeSchema();
        String sql = "SELECT key_code, label, key_scope FROM pricing_strategy_keys ORDER BY key_code";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(row(rs));
            }
        } catch (Exception e) {
            throw new RuntimeException("list strategy keys failed", e);
        }
        return list;
    }

    public Map<String, Object> create(String keyCode, String label, String scope) {
        SchemaMigrationHelper.ensureRuntimeSchema();
        String code = normalize(keyCode);
        String sc = "GLOBAL".equalsIgnoreCase(scope) ? "GLOBAL" : "SLOT";
        if (label == null || label.trim().isEmpty()) {
            label = code;
        }
        String sql = "INSERT INTO pricing_strategy_keys (key_code, label, key_scope) VALUES (?, ?, ?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, label.trim());
            ps.setString(3, sc);
            ps.executeUpdate();
        } catch (Exception e) {
            if (String.valueOf(e.getMessage()).toLowerCase().contains("duplicate")) {
                throw new IllegalArgumentException("Strategy key already exists: " + code);
            }
            throw new RuntimeException("create strategy key failed", e);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("keyCode", code);
        m.put("label", label.trim());
        m.put("scope", sc);
        return m;
    }

    public void ensureKey(String keyCode, String label, String scope) {
        String code = normalize(keyCode);
        String sql = "INSERT IGNORE INTO pricing_strategy_keys (key_code, label, key_scope) VALUES (?, ?, ?)";
        String sc = "GLOBAL".equalsIgnoreCase(scope) ? "GLOBAL" : "SLOT";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, label == null || label.trim().isEmpty() ? code : label.trim());
            ps.setString(3, sc);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("ensure strategy key failed", e);
        }
    }

    public boolean exists(String keyCode) {
        String sql = "SELECT 1 FROM pricing_strategy_keys WHERE key_code = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, normalize(keyCode));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> row(ResultSet rs) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("keyCode", rs.getString("key_code"));
        m.put("label", rs.getString("label"));
        m.put("scope", rs.getString("key_scope"));
        return m;
    }
}
