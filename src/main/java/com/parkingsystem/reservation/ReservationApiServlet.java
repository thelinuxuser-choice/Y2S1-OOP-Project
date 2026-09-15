package com.parkingsystem.reservation;

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
import java.time.LocalDateTime;

@WebServlet("/api/reservations/*")
public class ReservationApiServlet extends HttpServlet {

    private final ReservationService service = new ReservationService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = SessionHelper.requireUser(req);
        if (u == null) {
            JsonUtil.fail(resp, 401, "Login required");
            return;
        }
        String path = path(req);
        if (path.isEmpty() || "/".equals(path) || "/history".equals(path)) {
            JsonUtil.ok(resp, service.dao().listByUser(u.getUserId()));
            return;
        }
        if (path.startsWith("/")) {
            try {
                int id = Integer.parseInt(path.substring(1));
                Reservation r = service.dao().findById(id);
                if (r == null || (r.getUserId() != u.getUserId()
                        && !SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN, UserRole.ATTENDANT))) {
                    JsonUtil.fail(resp, 404, "Not found");
                    return;
                }
                JsonUtil.ok(resp, r);
            } catch (NumberFormatException e) {
                JsonUtil.fail(resp, 400, "Bad id");
            }
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
        String path = path(req);
        JsonObject body = read(req);

        try {
            if ("/expire-locks".equals(path)) {
                if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN, UserRole.ATTENDANT)) {
                    JsonUtil.fail(resp, 403, "Staff only");
                    return;
                }
                JsonUtil.ok(resp, java.util.Collections.singletonMap("expired", service.expireStaleLocks()));
                return;
            }
            if (u.getRole() != UserRole.CUSTOMER) {
                JsonUtil.fail(resp, 401, "Customer login required");
                return;
            }
            if (path.isEmpty() || "/".equals(path) || "/create".equals(path)) {
                int slotId = body.get("slotId").getAsInt();
                Integer vehicleId = body.has("vehicleId") && !body.get("vehicleId").isJsonNull()
                        ? body.get("vehicleId").getAsInt() : null;
                LocalDateTime start = LocalDateTime.parse(body.get("startTime").getAsString());
                LocalDateTime end = LocalDateTime.parse(body.get("endTime").getAsString());
                Reservation r = service.create(u.getUserId(), slotId, vehicleId, start, end);
                JsonUtil.ok(resp, r);
                return;
            }
            if (path.startsWith("/cancel/")) {
                int id = Integer.parseInt(path.substring("/cancel/".length()));
                JsonUtil.ok(resp, service.cancel(id, u.getUserId()));
                return;
            }
            if (path.startsWith("/reschedule/")) {
                int id = Integer.parseInt(path.substring("/reschedule/".length()));
                LocalDateTime start = LocalDateTime.parse(body.get("startTime").getAsString());
                LocalDateTime end = LocalDateTime.parse(body.get("endTime").getAsString());
                JsonUtil.ok(resp, service.reschedule(id, u.getUserId(), start, end));
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

    private String path(HttpServletRequest req) {
        String p = req.getPathInfo();
        return p == null ? "" : p;
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
