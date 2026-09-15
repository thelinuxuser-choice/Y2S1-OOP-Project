package com.parkingsystem.payment;

import com.google.gson.JsonObject;
import com.parkingsystem.common.JsonUtil;
import com.parkingsystem.common.SessionHelper;
import com.parkingsystem.common.User;
import com.parkingsystem.common.UserRole;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;

@WebServlet("/api/payments/*")
public class PaymentApiServlet extends HttpServlet {

    private final PaymentService service = new PaymentService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = SessionHelper.requireUser(req);
        if (u == null) {
            JsonUtil.fail(resp, 401, "Login required");
            return;
        }
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        try {
            if (path.startsWith("/quote/")) {
                int resId = Integer.parseInt(path.substring("/quote/".length()));
                int pts = 0;
                if (req.getParameter("points") != null) {
                    pts = Integer.parseInt(req.getParameter("points"));
                }
                JsonUtil.ok(resp, service.quote(resId, pts));
                return;
            }
            if ("/revenue".equals(path)) {
                if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
                    JsonUtil.fail(resp, 403, "Manager only");
                    return;
                }
                JsonUtil.ok(resp, service.dao().listPaid());
                return;
            }
            if ("/mine".equals(path)) {
                JsonUtil.ok(resp, service.listForUser(u.getUserId()));
                return;
            }
            if (path.startsWith("/by-reservation/")) {
                int resId = Integer.parseInt(path.substring("/by-reservation/".length()));
                JsonUtil.ok(resp, service.dao().findByReservation(resId));
                return;
            }
        } catch (Exception e) {
            JsonUtil.fail(resp, 400, e.getMessage());
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
        JsonObject body = read(req);
        try {
            if ("/checkout".equals(path)) {
                int resId = body.get("reservationId").getAsInt();
                int pts = body.has("pointsToRedeem") ? body.get("pointsToRedeem").getAsInt() : 0;
                JsonUtil.ok(resp, service.checkout(u.getUserId(), resId, pts));
                return;
            }
            if (path.startsWith("/refund/")) {
                if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
                    JsonUtil.fail(resp, 403, "Manager only");
                    return;
                }
                int pid = Integer.parseInt(path.substring("/refund/".length()));
                JsonUtil.ok(resp, service.refund(pid, u.getUserId()));
                return;
            }
        } catch (IllegalArgumentException e) {
            JsonUtil.fail(resp, 400, e.getMessage());
            return;
        } catch (Exception e) {
            JsonUtil.fail(resp, 500, e.getMessage());
            return;
        }
        JsonUtil.fail(resp, 404, "Unknown");
    }

    private JsonObject read(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = req.getReader()) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        if (sb.length() == 0) {
            return new JsonObject();
        }
        return JsonUtil.gson().fromJson(sb.toString(), JsonObject.class);
    }
}
