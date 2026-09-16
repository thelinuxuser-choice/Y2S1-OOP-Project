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
import java.math.BigDecimal;

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
            if ("/rates".equals(path)) {
                JsonUtil.ok(resp, service.rateInfo());
                return;
            }
            if ("/rules".equals(path)) {
                if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
                    JsonUtil.fail(resp, 403, "Manager only");
                    return;
                }
                JsonUtil.ok(resp, service.rateRules().listActive());
                return;
            }
            if ("/keys".equals(path)) {
                if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
                    JsonUtil.fail(resp, 403, "Manager only");
                    return;
                }
                JsonUtil.ok(resp, service.strategyKeys().listAll());
                return;
            }
            if (path.startsWith("/quote/")) {
                int resId = Integer.parseInt(path.substring("/quote/".length()));
                int pts = 0;
                if (req.getParameter("points") != null) {
                    pts = Integer.parseInt(req.getParameter("points"));
                }
                JsonUtil.ok(resp, service.quote(resId, pts));
                return;
            }
            if ("/revenue/report".equals(path)) {
                if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
                    JsonUtil.fail(resp, 403, "Manager only");
                    return;
                }
                JsonUtil.ok(resp, service.revenueReport());
                return;
            }
            if ("/revenue".equals(path)) {
                if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
                    JsonUtil.fail(resp, 403, "Manager only");
                    return;
                }
                JsonUtil.ok(resp, service.dao().listAll());
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
                if (u.getRole() != UserRole.CUSTOMER) {
                    JsonUtil.fail(resp, 403, "Customer only");
                    return;
                }
                int resId = body.get("reservationId").getAsInt();
                int pts = body.has("pointsToRedeem") ? body.get("pointsToRedeem").getAsInt() : 0;
                JsonUtil.ok(resp, service.checkout(u.getUserId(), resId, pts));
                return;
            }
            if ("/keys".equals(path)) {
                if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
                    JsonUtil.fail(resp, 403, "Manager only");
                    return;
                }
                JsonUtil.ok(resp, service.strategyKeys().create(
                        body.get("keyCode").getAsString(),
                        body.has("label") && !body.get("label").isJsonNull()
                                ? body.get("label").getAsString() : null,
                        body.has("scope") ? body.get("scope").getAsString() : "SLOT"));
                return;
            }
            if ("/rules".equals(path)) {
                if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
                    JsonUtil.fail(resp, 403, "Manager only");
                    return;
                }
                JsonUtil.ok(resp, service.rateRules().create(
                        body.get("ruleName").getAsString(),
                        body.get("strategyKey").getAsString(),
                        body.get("multiplier").getAsBigDecimal()));
                return;
            }
            if (path.startsWith("/rules/update/")) {
                if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
                    JsonUtil.fail(resp, 403, "Manager only");
                    return;
                }
                int id = Integer.parseInt(path.substring("/rules/update/".length()));
                BigDecimal mult = body.has("multiplier") ? body.get("multiplier").getAsBigDecimal() : null;
                Boolean active = body.has("active") ? body.get("active").getAsBoolean() : null;
                if (!service.rateRules().update(id, mult, active)) {
                    JsonUtil.fail(resp, 404, "Rule not found");
                    return;
                }
                JsonUtil.ok(resp, java.util.Collections.singletonMap("updated", true));
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

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = SessionHelper.requireUser(req);
        if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
            JsonUtil.fail(resp, 403, "Manager only");
            return;
        }
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        if (path.startsWith("/rules/")) {
            try {
                int id = Integer.parseInt(path.substring("/rules/".length()));
                if (!service.rateRules().delete(id)) {
                    JsonUtil.fail(resp, 404, "Rule not found");
                    return;
                }
                JsonUtil.ok(resp, "deleted");
            } catch (Exception e) {
                JsonUtil.fail(resp, 400, e.getMessage());
            }
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
