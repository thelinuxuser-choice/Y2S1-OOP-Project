package com.parkingsystem.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.parkingsystem.common.adapters.LocalDateTimeAdapter;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public final class JsonUtil {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    private JsonUtil() {
    }

    public static Gson gson() {
        return GSON;
    }

    public static void write(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(GSON.toJson(body));
    }

    public static void ok(HttpServletResponse resp, Object data) throws IOException {
        Map<String, Object> wrap = new HashMap<>();
        wrap.put("ok", true);
        wrap.put("data", data);
        write(resp, 200, wrap);
    }

    public static void fail(HttpServletResponse resp, int status, String message) throws IOException {
        Map<String, Object> wrap = new HashMap<>();
        wrap.put("ok", false);
        wrap.put("error", message);
        write(resp, status, wrap);
    }
}
