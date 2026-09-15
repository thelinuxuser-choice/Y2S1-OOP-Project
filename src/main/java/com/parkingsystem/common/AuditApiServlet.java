package com.parkingsystem.common;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/audit/*")
public class AuditApiServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = SessionHelper.requireUser(req);
        if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
            JsonUtil.fail(resp, 403, "Manager access only");
            return;
        }
        int limit = 100;
        if (req.getParameter("limit") != null) {
            limit = Integer.parseInt(req.getParameter("limit"));
        }
        String sql = "SELECT log_id, user_id, action, details, created_at FROM audit_logs "
                + "ORDER BY created_at DESC LIMIT ?";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getInt("log_id"));
                    m.put("userId", rs.getObject("user_id"));
                    m.put("action", rs.getString("action"));
                    m.put("details", rs.getString("details"));
                    m.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
                    list.add(m);
                }
            }
        } catch (Exception e) {
            JsonUtil.fail(resp, 500, e.getMessage());
            return;
        }
        JsonUtil.ok(resp, list);
    }
}
