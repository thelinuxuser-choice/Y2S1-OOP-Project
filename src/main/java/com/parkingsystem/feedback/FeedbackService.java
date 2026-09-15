package com.parkingsystem.feedback;

import com.parkingsystem.common.DBConnection;
import com.parkingsystem.livemap.LiveMapModel;
import com.parkingsystem.livemap.Slot;
import com.parkingsystem.reservation.Reservation;
import com.parkingsystem.reservation.ReservationDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeedbackService {

    private final FeedbackFactory factory = new FeedbackFactory();
    private final ReservationDAO reservationDAO = new ReservationDAO();
    private final LiveMapModel mapModel = new LiveMapModel();

    public FeedbackEntity submit(int userId, int reservationId, int rating, String comment, String type) {
        Reservation r = reservationDAO.findById(reservationId);
        if (r == null || r.getUserId() != userId) {
            throw new IllegalArgumentException("Reservation not found");
        }
        if (!"COMPLETED".equals(r.getStatus())) {
            throw new IllegalArgumentException("Feedback only after a completed visit");
        }
        if (existsForReservation(reservationId)) {
            throw new IllegalArgumentException("You already rated this visit");
        }

        Slot slot = mapModel.findSlot(r.getSlotId());
        FeedbackEntity entity = factory.create(type);
        entity.setUserId(userId);
        entity.setReservationId(reservationId);
        entity.setFacilityId(slot.getFacilityId());
        entity.setRating(rating);
        entity.setCommentText(comment);
        entity.validate();
        persist(entity);
        return entity;
    }

    public List<Map<String, Object>> listRecent(int limit) {
        String sql = "SELECT f.*, u.full_name FROM feedback f JOIN users u ON u.user_id = f.user_id "
                + "ORDER BY f.created_at DESC LIMIT ?";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("feedbackId", rs.getInt("feedback_id"));
                    m.put("rating", rs.getInt("rating"));
                    m.put("comment", rs.getString("comment_text"));
                    m.put("type", rs.getString("feedback_type"));
                    m.put("customer", rs.getString("full_name"));
                    m.put("facilityId", rs.getInt("facility_id"));
                    m.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
                    list.add(m);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public Map<String, Object> aggregate(int facilityId) {
        String sql = "SELECT AVG(rating) AS avg_rating, COUNT(*) AS total FROM feedback WHERE facility_id = ?";
        Map<String, Object> m = new HashMap<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, facilityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    m.put("average", rs.getDouble("avg_rating"));
                    m.put("total", rs.getInt("total"));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return m;
    }

    public List<Map<String, Object>> listMine(int userId) {
        String sql = "SELECT * FROM feedback WHERE user_id = ? ORDER BY created_at DESC";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(row(rs));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public Map<String, Object> update(int userId, int feedbackId, int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be 1–5");
        }
        String sql = "UPDATE feedback SET rating = ?, comment_text = ?, "
                + "feedback_type = ? WHERE feedback_id = ? AND user_id = ?";
        String type = (comment == null || comment.trim().isEmpty()) ? "RATING_ONLY" : "RATING_WITH_COMMENT";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, rating);
            ps.setString(2, comment);
            ps.setString(3, type);
            ps.setInt(4, feedbackId);
            ps.setInt(5, userId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("Feedback not found");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("feedbackId", feedbackId);
        m.put("rating", rating);
        m.put("comment", comment);
        m.put("type", type);
        return m;
    }

    public boolean delete(int userId, int feedbackId) {
        String sql = "DELETE FROM feedback WHERE feedback_id = ? AND user_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, feedbackId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> row(ResultSet rs) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("feedbackId", rs.getInt("feedback_id"));
        m.put("reservationId", rs.getInt("reservation_id"));
        m.put("rating", rs.getInt("rating"));
        m.put("comment", rs.getString("comment_text"));
        m.put("type", rs.getString("feedback_type"));
        m.put("facilityId", rs.getInt("facility_id"));
        m.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
        return m;
    }

    private boolean existsForReservation(int reservationId) {
        String sql = "SELECT 1 FROM feedback WHERE reservation_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void persist(FeedbackEntity e) {
        String sql = "INSERT INTO feedback (reservation_id, user_id, facility_id, rating, comment_text, feedback_type) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, e.getReservationId());
            ps.setInt(2, e.getUserId());
            ps.setInt(3, e.getFacilityId());
            ps.setInt(4, e.getRating());
            ps.setString(5, e.getCommentText());
            ps.setString(6, e.getFeedbackType());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    e.setFeedbackId(keys.getInt(1));
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("feedback save failed", ex);
        }
    }
}
