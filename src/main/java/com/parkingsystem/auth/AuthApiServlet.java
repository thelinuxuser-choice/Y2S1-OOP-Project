package com.parkingsystem.auth;

import com.google.gson.JsonObject;
import com.parkingsystem.common.JsonUtil;
import com.parkingsystem.common.SessionHelper;
import com.parkingsystem.common.User;
import com.parkingsystem.common.Vehicle;
import com.parkingsystem.common.VehicleDAO;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/api/auth/*")
public class AuthApiServlet extends HttpServlet {

    private final AuthService authService = new AuthService();
    private final VehicleDAO vehicleDAO = new VehicleDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = pathInfo(req);
        if ("/me".equals(path)) {
            User u = SessionHelper.requireUser(req);
            if (u == null) {
                JsonUtil.fail(resp, 401, "Not logged in");
                return;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("user", u);
            data.put("vehicles", vehicleDAO.listByUser(u.getUserId()));
            JsonUtil.ok(resp, data);
            return;
        }
        JsonUtil.fail(resp, 404, "Unknown auth route");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = pathInfo(req);
        JsonObject body = readBody(req);

        if ("/login".equals(path)) {
            String email = str(body, "email");
            String password = str(body, "password");
            User u = authService.login(email, password);
            if (u == null) {
                JsonUtil.fail(resp, 401, "Invalid email or password");
                return;
            }
            HttpSession session = req.getSession(true);
            session.setAttribute(SessionHelper.CURRENT_USER, u);
            JsonUtil.ok(resp, u);
            return;
        }

        if ("/register".equals(path)) {
            try {
                String pass = str(body, "password");
                String confirm = str(body, "confirmPassword");
                if (pass == null || !pass.equals(confirm)) {
                    JsonUtil.fail(resp, 400, "Passwords do not match");
                    return;
                }
                User u = authService.registerCustomer(
                        str(body, "fullName"),
                        str(body, "email"),
                        str(body, "phone"),
                        pass);
                JsonUtil.ok(resp, u);
            } catch (IllegalArgumentException ex) {
                JsonUtil.fail(resp, 400, ex.getMessage());
            }
            return;
        }

        if ("/logout".equals(path)) {
            HttpSession session = req.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            JsonUtil.ok(resp, "logged out");
            return;
        }

        if ("/vehicles".equals(path)) {
            User u = SessionHelper.requireUser(req);
            if (u == null) {
                JsonUtil.fail(resp, 401, "Not logged in");
                return;
            }
            Vehicle v = new Vehicle();
            v.setUserId(u.getUserId());
            v.setPlateNumber(str(body, "plateNumber"));
            v.setVehicleType(str(body, "vehicleType") == null ? "CAR" : str(body, "vehicleType"));
            if (v.getPlateNumber() == null || v.getPlateNumber().trim().isEmpty()) {
                JsonUtil.fail(resp, 400, "Plate number required");
                return;
            }
            int id = vehicleDAO.insert(v);
            v.setVehicleId(id);
            JsonUtil.ok(resp, v);
            return;
        }

        if ("/profile".equals(path)) {
            User u = SessionHelper.requireUser(req);
            if (u == null) {
                JsonUtil.fail(resp, 401, "Not logged in");
                return;
            }
            String name = str(body, "fullName");
            String phone = str(body, "phone");
            if (name == null || name.trim().isEmpty()) {
                JsonUtil.fail(resp, 400, "Full name required");
                return;
            }
            new UserDAO().updateProfile(u.getUserId(), name.trim(), phone);
            User refreshed = new UserDAO().findById(u.getUserId());
            req.getSession(true).setAttribute(SessionHelper.CURRENT_USER, refreshed);
            JsonUtil.ok(resp, refreshed);
            return;
        }

        if (path != null && path.startsWith("/vehicles/") && path.endsWith("/update")) {
            User u = SessionHelper.requireUser(req);
            if (u == null) {
                JsonUtil.fail(resp, 401, "Not logged in");
                return;
            }
            int vid = Integer.parseInt(path.substring("/vehicles/".length(), path.length() - "/update".length()));
            boolean ok = vehicleDAO.update(vid, u.getUserId(),
                    str(body, "plateNumber"),
                    str(body, "vehicleType") == null ? "CAR" : str(body, "vehicleType"));
            if (!ok) {
                JsonUtil.fail(resp, 404, "Vehicle not found");
                return;
            }
            JsonUtil.ok(resp, vehicleDAO.findById(vid));
            return;
        }

        JsonUtil.fail(resp, 404, "Unknown auth route");
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = pathInfo(req);
        if (path != null && path.startsWith("/vehicles/")) {
            User u = SessionHelper.requireUser(req);
            if (u == null) {
                JsonUtil.fail(resp, 401, "Not logged in");
                return;
            }
            int vid = Integer.parseInt(path.substring("/vehicles/".length()));
            boolean ok = vehicleDAO.delete(vid, u.getUserId());
            if (!ok) {
                JsonUtil.fail(resp, 404, "Vehicle not found");
                return;
            }
            JsonUtil.ok(resp, "deleted");
            return;
        }
        JsonUtil.fail(resp, 404, "Unknown auth route");
    }

    private String pathInfo(HttpServletRequest req) {
        String p = req.getPathInfo();
        return p == null ? "" : p;
    }

    private JsonObject readBody(HttpServletRequest req) throws IOException {
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

    private String str(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        return o.get(key).getAsString();
    }
}
