package com.parkingsystem.inquiry;

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

@WebServlet("/api/inquiries/*")
public class InquiryApiServlet extends HttpServlet {

    private final InquiryService service = new InquiryService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = SessionHelper.requireUser(req);
        if (u == null) {
            JsonUtil.fail(resp, 401, "Login required");
            return;
        }
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        if (path.isEmpty() || "/".equals(path) || "/mine".equals(path)) {
            if (SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
                JsonUtil.ok(resp, service.listAll());
            } else {
                JsonUtil.ok(resp, service.listForUser(u.getUserId()));
            }
            return;
        }
        if (path.startsWith("/")) {
            try {
                int id = Integer.parseInt(path.substring(1).split("/")[0]);
                Inquiry inq = service.findById(id);
                if (inq == null) {
                    JsonUtil.fail(resp, 404, "Not found");
                    return;
                }
                if (inq.getUserId() != u.getUserId()
                        && !SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
                    JsonUtil.fail(resp, 403, "Forbidden");
                    return;
                }
                Map<String, Object> data = new HashMap<>();
                data.put("inquiry", inq);
                data.put("replies", service.replies(id));
                JsonUtil.ok(resp, data);
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
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        JsonObject body = read(req);
        try {
            if (path.isEmpty() || "/".equals(path) || "/create".equals(path)) {
                if (u.getRole() != UserRole.CUSTOMER) {
                    JsonUtil.fail(resp, 403, "Customers open tickets");
                    return;
                }
                Inquiry inq = service.create(
                        u.getUserId(),
                        body.has("category") ? body.get("category").getAsString() : "GENERAL",
                        body.get("subject").getAsString(),
                        body.get("description").getAsString(),
                        body.has("relatedRef") && !body.get("relatedRef").isJsonNull()
                                ? body.get("relatedRef").getAsString() : null);
                JsonUtil.ok(resp, inq);
                return;
            }
            if ("/reply".equals(path)) {
                if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
                    JsonUtil.fail(resp, 403, "Staff only");
                    return;
                }
                String refOrId = body.has("referenceNo") && !body.get("referenceNo").isJsonNull()
                        ? body.get("referenceNo").getAsString()
                        : String.valueOf(body.get("inquiryId").getAsInt());
                int id = service.resolveInquiryId(refOrId);
                service.reply(id, u.getUserId(), body.get("message").getAsString());
                JsonUtil.ok(resp, "replied");
                return;
            }
            if (path.startsWith("/reply/")) {
                if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
                    JsonUtil.fail(resp, 403, "Staff only");
                    return;
                }
                String refOrId = path.substring("/reply/".length());
                int id = service.resolveInquiryId(java.net.URLDecoder.decode(refOrId, "UTF-8"));
                service.reply(id, u.getUserId(), body.get("message").getAsString());
                JsonUtil.ok(resp, "replied");
                return;
            }
            if (path.startsWith("/transition/")) {
                if (!SessionHelper.hasRole(u, UserRole.MANAGER, UserRole.ADMIN)) {
                    JsonUtil.fail(resp, 403, "Staff only");
                    return;
                }
                int id = Integer.parseInt(path.substring("/transition/".length()));
                String action = body.get("action").getAsString();
                JsonUtil.ok(resp, service.transition(id, action));
                return;
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
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
