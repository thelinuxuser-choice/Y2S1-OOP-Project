package com.parkingsystem.common;

import java.sql.Connection;
import java.sql.PreparedStatement;

// quick trail for payment / loyalty / inquiry sensitive actions
public final class AuditLogger {

    private AuditLogger() {
    }

    public static void log(Integer userId, String action, String details) {
        String sql = "INSERT INTO audit_logs (user_id, action, details) VALUES (?, ?, ?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (userId == null) {
                ps.setObject(1, null);
            } else {
                ps.setInt(1, userId);
            }
            ps.setString(2, action);
            ps.setString(3, details);
            ps.executeUpdate();
        } catch (Exception ignored) {
            // don't blow up business flow if audit insert fails
        }
    }
}
