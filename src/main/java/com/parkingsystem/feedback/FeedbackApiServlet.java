package com.parkingsystem.feedback;

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

@WebServlet("/api/feedback/*")
public class FeedbackApiServlet extends HttpServlet {

    private final FeedbackService service = new FeedbackService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = SessionHelper.requireUser(req);
        if (u == null) {
            JsonUtil.fail(resp, 401, "Login required");
            return;
        }
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        if ("/all".equals(path)) {
            if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
                JsonUtil.fail(resp, 403, "Manager only");
                return;
            }
            JsonUtil.ok(resp, service.listAllForManager());
            return;
        }
        if ("/recent".equals(path)) {
            if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
                JsonUtil.fail(resp, 403, "Manager only");
                return;
            }
            JsonUtil.ok(resp, service.listRecent(50));
            return;
        }
        if ("/summary".equals(path)) {
            int facilityId = 1;
            if (req.getParameter("facilityId") != null) {
                facilityId = Integer.parseInt(req.getParameter("facilityId"));
            }
            JsonUtil.ok(resp, service.aggregate(facilityId));
            return;
        }
        if ("/mine".equals(path)) {
            JsonUtil.ok(resp, service.listMine(u.getUserId()));
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
            if (path.startsWith("/manager/update/")) {
                if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
                    JsonUtil.fail(resp, 403, "Manager only");
                    return;
                }
                int id = Integer.parseInt(path.substring("/manager/update/".length()));
                JsonUtil.ok(resp, service.updateByManager(
                        id,
                        body.get("rating").getAsInt(),
                        body.has("comment") && !body.get("comment").isJsonNull()
                                ? body.get("comment").getAsString() : null));
                return;
            }
            if (path.startsWith("/update/")) {
                if (u.getRole() != UserRole.CUSTOMER) {
                    JsonUtil.fail(resp, 403, "Customer only");
                    return;
                }
                int id = Integer.parseInt(path.substring("/update/".length()));
                JsonUtil.ok(resp, service.update(
                        u.getUserId(),
                        id,
                        body.get("rating").getAsInt(),
                        body.has("comment") && !body.get("comment").isJsonNull()
                                ? body.get("comment").getAsString() : null));
                return;
            }
            if (path.isEmpty() || "/".equals(path) || "/create".equals(path)) {
                if (u.getRole() != UserRole.CUSTOMER) {
                    JsonUtil.fail(resp, 403, "Customer only");
                    return;
                }
                FeedbackEntity e = service.submit(
                        u.getUserId(),
                        body.get("reservationId").getAsInt(),
                        body.get("rating").getAsInt(),
                        body.has("comment") && !body.get("comment").isJsonNull()
                                ? body.get("comment").getAsString() : null,
                        body.has("type") ? body.get("type").getAsString() : "RATING_ONLY");
                JsonUtil.ok(resp, e);
                return;
            }
        } catch (IllegalArgumentException ex) {
            JsonUtil.fail(resp, 400, ex.getMessage());
            return;
        } catch (Exception ex) {
            JsonUtil.fail(resp, 500, ex.getMessage());
            return;
        }
        JsonUtil.fail(resp, 404, "Unknown");
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = SessionHelper.requireUser(req);
        if (u == null) {
            JsonUtil.fail(resp, 401, "Login required");
            return;
        }
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        if (path.startsWith("/")) {
            try {
                int id = Integer.parseInt(path.substring(1));
                boolean ok;
                if (SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
                    ok = service.deleteByManager(id);
                } else if (u.getRole() == UserRole.CUSTOMER) {
                    ok = service.delete(u.getUserId(), id);
                } else {
                    JsonUtil.fail(resp, 403, "Not allowed");
                    return;
                }
                if (!ok) {
                    JsonUtil.fail(resp, 404, "Feedback not found");
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
