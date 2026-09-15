package com.parkingsystem.common;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class VehicleDAO {

    public List<Vehicle> listByUser(int userId) {
        String sql = "SELECT vehicle_id, user_id, plate_number, vehicle_type FROM vehicles WHERE user_id = ?";
        List<Vehicle> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("vehicle list failed", e);
        }
        return list;
    }

    public Vehicle findById(int id) {
        String sql = "SELECT vehicle_id, user_id, plate_number, vehicle_type FROM vehicles WHERE vehicle_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("vehicle find failed", e);
        }
        return null;
    }

    public int insert(Vehicle v) {
        String sql = "INSERT INTO vehicles (user_id, plate_number, vehicle_type) VALUES (?, ?, ?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, v.getUserId());
            ps.setString(2, v.getPlateNumber());
            ps.setString(3, v.getVehicleType());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("vehicle insert failed", e);
        }
        return -1;
    }

    public boolean delete(int vehicleId, int userId) {
        String sql = "DELETE FROM vehicles WHERE vehicle_id = ? AND user_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            throw new RuntimeException("vehicle delete failed", e);
        }
    }

    public boolean update(int vehicleId, int userId, String plate, String type) {
        if (plate == null || plate.trim().isEmpty()) {
            throw new IllegalArgumentException("Plate number required");
        }
        String sql = "UPDATE vehicles SET plate_number = ?, vehicle_type = ? WHERE vehicle_id = ? AND user_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, plate.trim().toUpperCase());
            ps.setString(2, type == null ? "CAR" : type.toUpperCase());
            ps.setInt(3, vehicleId);
            ps.setInt(4, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            throw new RuntimeException("vehicle update failed", e);
        }
    }

    private Vehicle map(ResultSet rs) throws Exception {
        Vehicle v = new Vehicle();
        v.setVehicleId(rs.getInt("vehicle_id"));
        v.setUserId(rs.getInt("user_id"));
        v.setPlateNumber(rs.getString("plate_number"));
        v.setVehicleType(rs.getString("vehicle_type"));
        return v;
    }
}
