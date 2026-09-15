package com.parkingsystem.common;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NotificationService {

    private NotificationService() {
    }

    public static void notify(int userId, String title, String body) {
        String sql = "INSERT INTO notifications (user_id, title, body) VALUES (?, ?, ?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, title);
            ps.setString(3, body);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("notify failed", e);
        }
    }

    public static List<Map<String, Object>> listForUser(int userId, int limit) {
        String sql = "SELECT notification_id, title, body, is_read, created_at FROM notifications "
                + "WHERE user_id = ? ORDER BY created_at DESC LIMIT ?";
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("notification_id"));
                    row.put("title", rs.getString("title"));
                    row.put("body", rs.getString("body"));
                    row.put("read", rs.getBoolean("is_read"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    row.put("createdAt", ts != null ? ts.toLocalDateTime().toString() : null);
                    out.add(row);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("list notifications failed", e);
        }
        return out;
    }
}
