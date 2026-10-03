package com.parkingsystem.admin;

import com.google.gson.JsonObject;
import com.parkingsystem.common.AuditLogger;
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

// M.5 Configure Facility & Slots – manager/admin
@WebServlet("/api/admin/*")
public class FacilityAdminServlet extends HttpServlet {

    private final FacilityAdminService service = new FacilityAdminService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!guard(req, resp)) {
            return;
        }
        String path = path(req);
        if ("/facilities".equals(path) || path.isEmpty() || "/".equals(path)) {
            JsonUtil.ok(resp, service.listFacilities());
            return;
        }
        if ("/floors".equals(path)) {
            int facilityId = 1;
            if (req.getParameter("facilityId") != null) {
                facilityId = Integer.parseInt(req.getParameter("facilityId"));
            }
            JsonUtil.ok(resp, service.listFloors(facilityId));
            return;
        }
        JsonUtil.fail(resp, 404, "Unknown admin route");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = SessionHelper.requireUser(req);
        if (!guard(req, resp)) {
            return;
        }
        String path = path(req);
        JsonObject body = read(req);
        try {
            if ("/facilities".equals(path)) {
                JsonUtil.ok(resp, service.createFacility(
                        body.get("name").getAsString(),
                        body.get("location").getAsString()));
                AuditLogger.log(u.getUserId(), "ADMIN_FACILITY_CREATE", body.get("name").getAsString());
                return;
            }
            if ("/floors".equals(path)) {
                JsonUtil.ok(resp, service.createFloor(
                        body.get("facilityId").getAsInt(),
                        body.get("floorLabel").getAsString()));
                return;
            }
            if ("/slots".equals(path)) {
                BigDecimal rate = body.has("baseRate")
                        ? body.get("baseRate").getAsBigDecimal() : new BigDecimal("100");
                JsonUtil.ok(resp, service.createSlot(
                        body.get("floorId").getAsInt(),
                        body.get("slotCode").getAsString(),
                        body.has("zoneLabel") ? body.get("zoneLabel").getAsString() : null,
                        body.has("slotType") ? body.get("slotType").getAsString() : "STANDARD",
                        rate,
                        body.has("posRow") ? body.get("posRow").getAsInt() : 0,
                        body.has("posCol") ? body.get("posCol").getAsInt() : 0,
                        body.has("rateStrategyKey") && !body.get("rateStrategyKey").isJsonNull()
                                ? body.get("rateStrategyKey").getAsString() : null));
                AuditLogger.log(u.getUserId(), "ADMIN_SLOT_CREATE", body.get("slotCode").getAsString());
                return;
            }
            if (path.startsWith("/slots/") && path.endsWith("/update")) {
                int id = Integer.parseInt(path.substring("/slots/".length(), path.length() - "/update".length()));
                Integer moveFloor = body.has("floorId") && !body.get("floorId").isJsonNull()
                        ? body.get("floorId").getAsInt() : null;
                boolean ok = service.updateSlot(
                        id,
                        body.has("slotType") ? body.get("slotType").getAsString() : null,
                        body.has("baseRate") ? body.get("baseRate").getAsBigDecimal() : null,
                        body.has("status") ? body.get("status").getAsString() : null,
                        body.has("zoneLabel") ? body.get("zoneLabel").getAsString() : null,
                        body.has("rateStrategyKey") && !body.get("rateStrategyKey").isJsonNull()
                                ? body.get("rateStrategyKey").getAsString() : null,
                        moveFloor);
                if (!ok) {
                    JsonUtil.fail(resp, 404, "Slot not found");
                    return;
                }
                AuditLogger.log(u.getUserId(), "ADMIN_SLOT_UPDATE", "slotId=" + id);
                JsonUtil.ok(resp, MapOf("updated", true, "slotId", id));
                return;
            }
            if (path.startsWith("/slots/") && path.endsWith("/delete")) {
                int id = Integer.parseInt(path.substring("/slots/".length(), path.length() - "/delete".length()));
                boolean ok = service.deleteSlot(id);
                if (!ok) {
                    JsonUtil.fail(resp, 404, "Slot not found");
                    return;
                }
                AuditLogger.log(u.getUserId(), "ADMIN_SLOT_DELETE", "slotId=" + id);
                JsonUtil.ok(resp, MapOf("deleted", true, "slotId", id));
                return;
            }
            if (path.startsWith("/facilities/") && path.endsWith("/active")) {
                int id = Integer.parseInt(path.substring("/facilities/".length(), path.length() - "/active".length()));
                boolean active = body.has("active") && body.get("active").getAsBoolean();
                JsonUtil.ok(resp, MapOf("updated", service.setFacilityActive(id, active)));
                return;
            }
        } catch (IllegalArgumentException e) {
            JsonUtil.fail(resp, 400, e.getMessage());
            return;
        } catch (Exception e) {
            JsonUtil.fail(resp, 500, e.getMessage());
            return;
        }
        JsonUtil.fail(resp, 404, "Unknown admin route");
    }

    private boolean guard(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = SessionHelper.requireUser(req);
        if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
            JsonUtil.fail(resp, 403, "Manager access only");
            return false;
        }
        return true;
    }

    private static java.util.Map<String, Object> MapOf(Object... kv) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
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
