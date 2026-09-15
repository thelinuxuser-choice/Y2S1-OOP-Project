package com.parkingsystem.loyalty;

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
import java.util.HashMap;
import java.util.Map;

@WebServlet("/api/loyalty/*")
public class LoyaltyApiServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = SessionHelper.requireUser(req);
        if (u == null) {
            JsonUtil.fail(resp, 401, "Login required");
            return;
        }
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        LoyaltyProgramManager mgr = LoyaltyProgramManager.getInstance();

        if ("/dashboard".equals(path) || path.isEmpty() || "/".equals(path)) {
            Map<String, Object> acc = mgr.getAccount(u.getUserId());
            Map<String, Object> data = new HashMap<>();
            data.put("account", acc);
            data.put("history", mgr.history(u.getUserId()));
            data.put("rules", mgr.getRules());
            JsonUtil.ok(resp, data);
            return;
        }
        if ("/rules".equals(path)) {
            JsonUtil.ok(resp, mgr.getRules());
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
        LoyaltyProgramManager mgr = LoyaltyProgramManager.getInstance();
        try {
            if ("/enroll".equals(path)) {
                if (u.getRole() != UserRole.CUSTOMER) {
                    JsonUtil.fail(resp, 403, "Customers only");
                    return;
                }
                JsonUtil.ok(resp, mgr.enroll(u.getUserId()));
                return;
            }
            if ("/unenroll".equals(path)) {
                if (u.getRole() != UserRole.CUSTOMER) {
                    JsonUtil.fail(resp, 403, "Customers only");
                    return;
                }
                JsonUtil.ok(resp, java.util.Collections.singletonMap("removed", mgr.unenroll(u.getUserId())));
                return;
            }
            if ("/redeem".equals(path)) {
                if (u.getRole() != UserRole.CUSTOMER) {
                    JsonUtil.fail(resp, 403, "Customers only");
                    return;
                }
                int pts = body.get("points").getAsInt();
                String ref = body.has("reference") ? body.get("reference").getAsString() : "MANUAL";
                mgr.redeemPoints(u.getUserId(), pts, ref);
                JsonUtil.ok(resp, mgr.getAccount(u.getUserId()));
                return;
            }
            if ("/rules".equals(path)) {
                if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
                    JsonUtil.fail(resp, 403, "Manager only");
                    return;
                }
                Map<String, String> updates = new HashMap<>();
                body.entrySet().forEach(e -> updates.put(e.getKey(), e.getValue().getAsString()));
                JsonUtil.ok(resp, mgr.saveRules(updates));
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
