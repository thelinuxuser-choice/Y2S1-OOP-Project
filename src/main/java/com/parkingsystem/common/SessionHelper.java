package com.parkingsystem.common;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public final class SessionHelper {

    public static final String CURRENT_USER = "currentUser";

    private SessionHelper() {
    }

    public static User requireUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) {
            return null;
        }
        Object u = session.getAttribute(CURRENT_USER);
        return u instanceof User ? (User) u : null;
    }

    public static boolean hasRole(User user, UserRole... roles) {
        if (user == null) {
            return false;
        }
        for (UserRole r : roles) {
            if (user.getRole() == r) {
                return true;
            }
        }
        return false;
    }
}
