package com.parkingsystem.auth;

import com.parkingsystem.common.PasswordUtil;
import com.parkingsystem.common.User;
import com.parkingsystem.common.UserRole;

public class AuthService {

    private final UserDAO userDAO = new UserDAO();

    public User login(String email, String password) {
        if (email == null || password == null) {
            return null;
        }
        String hash = userDAO.getPasswordHash(email.trim().toLowerCase());
        if (hash == null || !PasswordUtil.matches(password, hash)) {
            return null;
        }
        User u = userDAO.findByEmail(email.trim().toLowerCase());
        if (u == null || !u.isActive()) {
            return null;
        }
        return u;
    }

    public User registerCustomer(String fullName, String email, String phone, String password) {
        if (blank(fullName) || blank(email) || blank(password)) {
            throw new IllegalArgumentException("Name, email and password are required");
        }
        if (!email.contains("@")) {
            throw new IllegalArgumentException("Enter a valid email");
        }
        if (password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        String normalized = email.trim().toLowerCase();
        if (userDAO.findByEmail(normalized) != null) {
            throw new IllegalArgumentException("Email already registered");
        }
        User u = new User();
        u.setFullName(fullName.trim());
        u.setEmail(normalized);
        u.setPhone(phone);
        u.setRole(UserRole.CUSTOMER);
        int id = userDAO.insert(u, PasswordUtil.hash(password));
        u.setUserId(id);
        u.setActive(true);
        return u;
    }

    private boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
