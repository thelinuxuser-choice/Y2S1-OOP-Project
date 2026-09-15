package com.parkingsystem.admin;

import com.parkingsystem.common.DBConnection;
import com.parkingsystem.livemap.SlotStatus;
import com.parkingsystem.livemap.SlotType;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// facility / slot config (M.5) – manager/admin CRUD for live map layout
public class FacilityAdminService {

    public List<Map<String, Object>> listFacilities() {
        String sql = "SELECT facility_id, name, location, is_active FROM facilities ORDER BY facility_id";
        return query(sql, null);
    }

    public Map<String, Object> createFacility(String name, String location) {
        if (name == null || name.trim().isEmpty() || location == null || location.trim().isEmpty()) {
            throw new IllegalArgumentException("Name and location required");
        }
        String sql = "INSERT INTO facilities (name, location, is_active) VALUES (?, ?, 1)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name.trim());
            ps.setString(2, location.trim());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                Map<String, Object> m = new HashMap<>();
                m.put("facilityId", keys.getInt(1));
                m.put("name", name.trim());
                m.put("location", location.trim());
                m.put("active", true);
                return m;
            }
        } catch (Exception e) {
            throw new RuntimeException("create facility failed", e);
        }
    }

    public Map<String, Object> createFloor(int facilityId, String label) {
        if (label == null || label.trim().isEmpty()) {
            throw new IllegalArgumentException("Floor label required");
        }
        String sql = "INSERT INTO floors (facility_id, floor_label) VALUES (?, ?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, facilityId);
            ps.setString(2, label.trim());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                Map<String, Object> m = new HashMap<>();
                m.put("floorId", keys.getInt(1));
                m.put("facilityId", facilityId);
                m.put("floorLabel", label.trim());
                return m;
            }
        } catch (Exception e) {
            throw new RuntimeException("create floor failed", e);
        }
    }

    public Map<String, Object> createSlot(int floorId, String code, String zone, String type,
                                          BigDecimal rate, int row, int col) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Slot code required");
        }
        SlotType.valueOf(type == null ? "STANDARD" : type.toUpperCase());
        String sql = "INSERT INTO slots (floor_id, slot_code, zone_label, slot_type, status, base_rate, pos_row, pos_col) "
                + "VALUES (?, ?, ?, ?, 'AVAILABLE', ?, ?, ?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, floorId);
            ps.setString(2, code.trim().toUpperCase());
            ps.setString(3, zone);
            ps.setString(4, type == null ? "STANDARD" : type.toUpperCase());
            ps.setBigDecimal(5, rate == null ? new BigDecimal("100.00") : rate);
            ps.setInt(6, row);
            ps.setInt(7, col);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                Map<String, Object> m = new HashMap<>();
                m.put("slotId", keys.getInt(1));
                m.put("floorId", floorId);
                m.put("slotCode", code.trim().toUpperCase());
                return m;
            }
        } catch (Exception e) {
            throw new RuntimeException("create slot failed: " + e.getMessage(), e);
        }
    }

    public boolean updateSlot(int slotId, String type, BigDecimal rate, String status, String zone) {
        if (status != null) {
            SlotStatus.valueOf(status.toUpperCase());
        }
        if (type != null) {
            SlotType.valueOf(type.toUpperCase());
        }
        StringBuilder sql = new StringBuilder("UPDATE slots SET ");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (type != null) {
            sql.append("slot_type = ?, ");
            args.add(type.toUpperCase());
        }
        if (rate != null) {
            sql.append("base_rate = ?, ");
            args.add(rate);
        }
        if (status != null) {
            sql.append("status = ?, ");
            args.add(status.toUpperCase());
        }
        if (zone != null) {
            sql.append("zone_label = ?, ");
            args.add(zone);
        }
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Nothing to update");
        }
        sql.setLength(sql.length() - 2);
        sql.append(" WHERE slot_id = ?");
        args.add(slotId);
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) {
                Object a = args.get(i);
                if (a instanceof BigDecimal) {
                    ps.setBigDecimal(i + 1, (BigDecimal) a);
                } else if (a instanceof Integer) {
                    ps.setInt(i + 1, (Integer) a);
                } else {
                    ps.setString(i + 1, String.valueOf(a));
                }
            }
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            throw new RuntimeException("update slot failed", e);
        }
    }

    public boolean deactivateSlot(int slotId) {
        // soft-delete = MAINTENANCE so bay stays off the bookable pool
        String sql = "UPDATE slots SET status = 'MAINTENANCE' WHERE slot_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, slotId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            throw new RuntimeException("deactivate slot failed", e);
        }
    }

    public boolean setFacilityActive(int facilityId, boolean active) {
        String sql = "UPDATE facilities SET is_active = ? WHERE facility_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, facilityId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            throw new RuntimeException("facility update failed", e);
        }
    }

    private List<Map<String, Object>> query(String sql, Integer id) {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (id != null) {
                ps.setInt(1, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("facilityId", rs.getInt("facility_id"));
                    m.put("name", rs.getString("name"));
                    m.put("location", rs.getString("location"));
                    m.put("active", rs.getBoolean("is_active"));
                    list.add(m);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return list;
    }
}
