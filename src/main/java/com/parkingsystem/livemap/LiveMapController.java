package com.parkingsystem.livemap;

import com.parkingsystem.common.JsonUtil;
import com.parkingsystem.common.SessionHelper;
import com.parkingsystem.common.User;
import com.parkingsystem.common.UserRole;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// controller only wires request -> model -> json (view is HTML/JS map)
@WebServlet("/api/map/*")
public class LiveMapController extends HttpServlet {

    private final LiveMapModel model = new LiveMapModel();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();

        if ("/facilities".equals(path)) {
            JsonUtil.ok(resp, model.listFacilities());
            return;
        }

        if ("/floors".equals(path)) {
            int facilityId = intParam(req, "facilityId", 1);
            JsonUtil.ok(resp, model.listFloors(facilityId));
            return;
        }

        if ("/slots".equals(path) || "/refresh".equals(path)) {
            int facilityId = intParam(req, "facilityId", 1);
            String type = req.getParameter("type");
            List<Slot> slots = model.listSlots(facilityId, type);
            Map<String, Object> payload = new HashMap<>();
            payload.put("facilityId", facilityId);
            payload.put("floors", model.listFloors(facilityId));
            payload.put("slots", slots);
            payload.put("legend", MapViewHelper.legend());
            JsonUtil.ok(resp, payload);
            return;
        }

        if (path.startsWith("/slot/")) {
            int id = Integer.parseInt(path.substring("/slot/".length()));
            Slot s = model.findSlot(id);
            if (s == null) {
                JsonUtil.fail(resp, 404, "Slot not found");
                return;
            }
            JsonUtil.ok(resp, s);
            return;
        }

        if ("/occupancy".equals(path)) {
            User u = SessionHelper.requireUser(req);
            if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
                JsonUtil.fail(resp, 403, "Manager access only");
                return;
            }
            int facilityId = intParam(req, "facilityId", 1);
            List<Slot> slots = model.listSlots(facilityId, null);
            Map<String, Long> counts = new HashMap<>();
            for (SlotStatus st : SlotStatus.values()) {
                counts.put(st.name(), slots.stream().filter(s -> s.getStatus() == st).count());
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("counts", counts);
            payload.put("slots", slots);
            JsonUtil.ok(resp, payload);
            return;
        }

        JsonUtil.fail(resp, 404, "Unknown map route");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = SessionHelper.requireUser(req);
        if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
            JsonUtil.fail(resp, 403, "Manager access only");
            return;
        }
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        if (path.startsWith("/slot/") && path.endsWith("/status")) {
            try {
                String mid = path.substring("/slot/".length(), path.length() - "/status".length());
                int id = Integer.parseInt(mid);
                com.google.gson.JsonObject body = read(req);
                String status = body.get("status").getAsString();
                SlotStatus st = SlotStatus.valueOf(status.toUpperCase());
                if (!model.updateStatus(id, st)) {
                    JsonUtil.fail(resp, 404, "Slot not found");
                    return;
                }
                Map<String, Object> out = new HashMap<>();
                out.put("slotId", id);
                out.put("status", st.name());
                JsonUtil.ok(resp, out);
            } catch (Exception e) {
                JsonUtil.fail(resp, 400, e.getMessage());
            }
            return;
        }
        JsonUtil.fail(resp, 404, "Unknown map route");
    }

    private com.google.gson.JsonObject read(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (java.io.BufferedReader br = req.getReader()) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        if (sb.length() == 0) {
            return new com.google.gson.JsonObject();
        }
        return JsonUtil.gson().fromJson(sb.toString(), com.google.gson.JsonObject.class);
    }

    private int intParam(HttpServletRequest req, String name, int def) {
        String v = req.getParameter(name);
        if (v == null || v.isEmpty()) {
            return def;
        }
        return Integer.parseInt(v);
    }
}
