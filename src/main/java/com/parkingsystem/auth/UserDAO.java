package com.parkingsystem.auth;

import com.parkingsystem.common.DBConnection;
import com.parkingsystem.common.User;
import com.parkingsystem.common.UserRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

// auth – shared minor
public class UserDAO {

    public User findByEmail(String email) {
        String sql = "SELECT user_id, full_name, email, password_hash, phone, role, is_active, created_at "
                + "FROM users WHERE email = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs, true);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("findByEmail failed", e);
        }
        return null;
    }

    public User findById(int id) {
        String sql = "SELECT user_id, full_name, email, phone, role, is_active, created_at "
                + "FROM users WHERE user_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs, false);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("findById failed", e);
        }
        return null;
    }

    // used only inside AuthService for password check
    public String getPasswordHash(String email) {
        String sql = "SELECT password_hash FROM users WHERE email = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("getPasswordHash failed", e);
        }
        return null;
    }

    public int insert(User user, String passwordHash) {
        String sql = "INSERT INTO users (full_name, email, password_hash, phone, role) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, passwordHash);
            ps.setString(4, user.getPhone());
            ps.setString(5, user.getRole().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("user insert failed", e);
        }
        return -1;
    }

    public boolean updateProfile(int userId, String fullName, String phone) {
        String sql = "UPDATE users SET full_name = ?, phone = ? WHERE user_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, phone);
            ps.setInt(3, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            throw new RuntimeException("updateProfile failed", e);
        }
    }

    public List<User> listByRole(UserRole role) {
        String sql = "SELECT user_id, full_name, email, phone, role, is_active, created_at "
                + "FROM users WHERE role = ? ORDER BY full_name";
        List<User> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, role.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs, false));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("listByRole failed", e);
        }
        return list;
    }

    private User map(ResultSet rs, boolean includeHashCol) throws Exception {
        User u = new User();
        u.setUserId(rs.getInt("user_id"));
        u.setFullName(rs.getString("full_name"));
        u.setEmail(rs.getString("email"));
        u.setPhone(rs.getString("phone"));
        u.setRole(UserRole.valueOf(rs.getString("role")));
        u.setActive(rs.getBoolean("is_active"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            u.setCreatedAt(ts.toLocalDateTime());
        }
        return u;
    }
}
