package com.parkingsystem.reservation;

import com.parkingsystem.common.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// reservation module – KYLM
public class ReservationDAO {

    public int create(Reservation r) {
        // temp lock 10 mins so two people don't grab same bay while paying
        r.setLockUntil(LocalDateTime.now().plusMinutes(10));
        r.setStatus("PENDING");
        r.setConfirmationToken(UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());

        String sql = "INSERT INTO reservations (user_id, slot_id, vehicle_id, start_time, end_time, status, "
                + "lock_until, confirmation_token) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.getUserId());
            ps.setInt(2, r.getSlotId());
            if (r.getVehicleId() == null) {
                ps.setObject(3, null);
            } else {
                ps.setInt(3, r.getVehicleId());
            }
            ps.setTimestamp(4, Timestamp.valueOf(r.getStartTime()));
            ps.setTimestamp(5, Timestamp.valueOf(r.getEndTime()));
            ps.setString(6, r.getStatus());
            ps.setTimestamp(7, Timestamp.valueOf(r.getLockUntil()));
            ps.setString(8, r.getConfirmationToken());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    r.setReservationId(id);
                    return id;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("create reservation failed", e);
        }
        return -1;
    }

    public boolean hasOverlap(int slotId, LocalDateTime start, LocalDateTime end, Integer excludeId) {
        String sql = "SELECT COUNT(*) FROM reservations WHERE slot_id = ? "
                + "AND status IN ('PENDING','ACTIVE') "
                + "AND start_time < ? AND end_time > ?";
        if (excludeId != null) {
            sql += " AND reservation_id <> ?";
        }
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, slotId);
            ps.setTimestamp(2, Timestamp.valueOf(end));
            ps.setTimestamp(3, Timestamp.valueOf(start));
            if (excludeId != null) {
                ps.setInt(4, excludeId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            throw new RuntimeException("overlap check failed", e);
        }
    }

    public Reservation findById(int id) {
        String sql = "SELECT * FROM reservations WHERE reservation_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("find reservation failed", e);
        }
        return null;
    }

    public List<Reservation> listByUser(int userId) {
        String sql = "SELECT * FROM reservations WHERE user_id = ? ORDER BY created_at DESC";
        List<Reservation> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("list reservations failed", e);
        }
        return list;
    }

    public boolean updateStatus(int id, String status) {
        String sql = "UPDATE reservations SET status = ? WHERE reservation_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            throw new RuntimeException("update status failed", e);
        }
    }

    public boolean updateTimes(int id, LocalDateTime start, LocalDateTime end) {
        String sql = "UPDATE reservations SET start_time = ?, end_time = ? WHERE reservation_id = ? "
                + "AND status IN ('PENDING','ACTIVE')";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(start));
            ps.setTimestamp(2, Timestamp.valueOf(end));
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            throw new RuntimeException("update times failed", e);
        }
    }

    public Reservation findByToken(String token) {
        String sql = "SELECT * FROM reservations WHERE confirmation_token = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("find by token failed", e);
        }
        return null;
    }

    /** Cancel expired unpaid holds and free the slot back to AVAILABLE. */
    public int expireStalePendingLocks() {
        String sql = "SELECT reservation_id, slot_id FROM reservations "
                + "WHERE status = 'PENDING' AND lock_until IS NOT NULL AND lock_until < NOW()";
        int n = 0;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int rid = rs.getInt("reservation_id");
                int sid = rs.getInt("slot_id");
                updateStatus(rid, "CANCELLED");
                try (PreparedStatement ups = c.prepareStatement(
                        "UPDATE slots SET status = 'AVAILABLE' WHERE slot_id = ? AND status = 'RESERVED'")) {
                    ups.setInt(1, sid);
                    ups.executeUpdate();
                }
                n++;
            }
        } catch (Exception e) {
            throw new RuntimeException("expire locks failed", e);
        }
        return n;
    }

    private Reservation map(ResultSet rs) throws Exception {
        Reservation r = new Reservation();
        r.setReservationId(rs.getInt("reservation_id"));
        r.setUserId(rs.getInt("user_id"));
        r.setSlotId(rs.getInt("slot_id"));
        int vid = rs.getInt("vehicle_id");
        r.setVehicleId(rs.wasNull() ? null : vid);
        r.setStartTime(rs.getTimestamp("start_time").toLocalDateTime());
        r.setEndTime(rs.getTimestamp("end_time").toLocalDateTime());
        r.setStatus(rs.getString("status"));
        Timestamp lock = rs.getTimestamp("lock_until");
        if (lock != null) {
            r.setLockUntil(lock.toLocalDateTime());
        }
        r.setConfirmationToken(rs.getString("confirmation_token"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            r.setCreatedAt(created.toLocalDateTime());
        }
        return r;
    }
}
