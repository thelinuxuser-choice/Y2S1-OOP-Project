package com.parkingsystem.common;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/api/notifications/*")
public class NotificationApiServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = SessionHelper.requireUser(req);
        if (u == null) {
            JsonUtil.fail(resp, 401, "Login required");
            return;
        }
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        if (path.isEmpty() || "/".equals(path) || "/mine".equals(path)) {
            JsonUtil.ok(resp, NotificationService.listForUser(u.getUserId(), 50));
            return;
        }
        JsonUtil.fail(resp, 404, "Unknown");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = SessionHelper.requireUser(req);
        if (u == null) {
            JsonUtil.fail(resp, 401, "Login required");
            return;
        }
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        if (path.startsWith("/read/")) {
            int id = Integer.parseInt(path.substring("/read/".length()));
            String sql = "UPDATE notifications SET is_read = 1 WHERE notification_id = ? AND user_id = ?";
            try (Connection c = DBConnection.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.setInt(2, u.getUserId());
                ps.executeUpdate();
            } catch (Exception e) {
                JsonUtil.fail(resp, 500, e.getMessage());
                return;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("read", true);
            m.put("id", id);
            JsonUtil.ok(resp, m);
            return;
        }
        if ("/read-all".equals(path)) {
            String sql = "UPDATE notifications SET is_read = 1 WHERE user_id = ?";
            try (Connection c = DBConnection.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, u.getUserId());
                ps.executeUpdate();
            } catch (Exception e) {
                JsonUtil.fail(resp, 500, e.getMessage());
                return;
            }
            JsonUtil.ok(resp, "ok");
            return;
        }
        JsonUtil.fail(resp, 404, "Unknown");
    }
}
