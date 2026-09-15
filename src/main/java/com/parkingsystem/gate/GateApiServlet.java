package com.parkingsystem.gate;

import com.google.gson.JsonObject;
import com.parkingsystem.common.JsonUtil;
import com.parkingsystem.common.SessionHelper;
import com.parkingsystem.common.User;
import com.parkingsystem.common.UserRole;
import com.parkingsystem.livemap.LiveMapModel;
import com.parkingsystem.livemap.SlotStatus;
import com.parkingsystem.payment.PaymentService;
import com.parkingsystem.reservation.Reservation;
import com.parkingsystem.reservation.ReservationService;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

// minor – attendant check-in / check-out by confirmation token
@WebServlet("/api/gate/*")
public class GateApiServlet extends HttpServlet {

    private final ReservationService reservationService = new ReservationService();
    private final LiveMapModel mapModel = new LiveMapModel();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = SessionHelper.requireUser(req);
        if (!SessionHelper.hasRole(u, UserRole.ATTENDANT, UserRole.ADMIN)) {
            JsonUtil.fail(resp, 403, "Attendant login required");
            return;
        }
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        JsonObject body = read(req);
        String token = body.has("token") ? body.get("token").getAsString() : null;
        if (token == null || token.trim().isEmpty()) {
            JsonUtil.fail(resp, 400, "Token required");
            return;
        }
        Reservation r = reservationService.dao().findByToken(token.trim().toUpperCase());
        if (r == null) {
            JsonUtil.fail(resp, 404, "No booking for that token");
            return;
        }
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("reservation", r);
            if ("/checkin".equals(path)) {
                reservationService.markActive(r.getReservationId());
                mapModel.updateStatus(r.getSlotId(), SlotStatus.OCCUPIED);
                result.put("action", "CHECKIN");
                JsonUtil.ok(resp, result);
                return;
            }
            if ("/checkout".equals(path)) {
                reservationService.complete(r.getReservationId());
                Map<String, Object> overstay = new PaymentService().chargeOverstayIfNeeded(r);
                mapModel.updateStatus(r.getSlotId(), SlotStatus.AVAILABLE);
                result.put("action", "CHECKOUT");
                result.put("overstay", overstay);
                JsonUtil.ok(resp, result);
                return;
            }
        } catch (Exception e) {
            JsonUtil.fail(resp, 400, e.getMessage());
            return;
        }
        JsonUtil.fail(resp, 404, "Use /checkin or /checkout");
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
