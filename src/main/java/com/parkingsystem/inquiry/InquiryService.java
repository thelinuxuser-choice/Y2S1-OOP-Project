package com.parkingsystem.inquiry;

import com.parkingsystem.common.AuditLogger;
import com.parkingsystem.common.DBConnection;
import com.parkingsystem.common.NotificationService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// inquiry module – WLTS
public class InquiryService {

    public Inquiry create(int userId, String category, String subject, String description, String relatedRef) {
        if (subject == null || subject.trim().isEmpty() || description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject and description required");
        }
        Inquiry inq = new Inquiry();
        inq.setUserId(userId);
        inq.setCategory(category == null ? "GENERAL" : category.toUpperCase());
        inq.setSubject(subject.trim());
        inq.setDescription(description.trim());
        inq.setRelatedRef(relatedRef);
        inq.setReferenceNo("INQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        inq.setStatus("OPEN");
        inq.bindState();

        String sql = "INSERT INTO inquiries (reference_no, user_id, category, subject, description, related_ref, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, inq.getReferenceNo());
            ps.setInt(2, userId);
            ps.setString(3, inq.getCategory());
            ps.setString(4, inq.getSubject());
            ps.setString(5, inq.getDescription());
            ps.setString(6, relatedRef);
            ps.setString(7, inq.getStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    inq.setInquiryId(keys.getInt(1));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("create inquiry failed", e);
        }
        NotificationService.notify(userId, "Inquiry opened", "Ref " + inq.getReferenceNo());
        return inq;
    }

    public Inquiry transition(int inquiryId, String action) {
        Inquiry inq = findById(inquiryId);
        if (inq == null) {
            throw new IllegalArgumentException("Inquiry not found");
        }
        inq.bindState();
        switch (action.toUpperCase()) {
            case "START":
                inq.getState().startProgress(inq);
                break;
            case "RESOLVE":
                inq.getState().resolve(inq);
                break;
            case "CLOSE":
                inq.getState().close(inq);
                break;
            case "ESCALATE":
                inq.getState().escalate(inq);
                break;
            case "REOPEN":
                inq.getState().reopen(inq);
                break;
            default:
                throw new IllegalArgumentException("Unknown action");
        }
        updateStatus(inq);
        NotificationService.notify(inq.getUserId(), "Inquiry " + inq.getStatus(),
                "Ref " + inq.getReferenceNo() + " is now " + inq.getStatus());
        AuditLogger.log(null, "INQUIRY_" + action, inq.getReferenceNo());
        return inq;
    }

    public void reply(int inquiryId, int staffId, String message) {
        Inquiry inq = findById(inquiryId);
        if (inq == null) {
            throw new IllegalArgumentException("Inquiry not found");
        }
        inq.bindState();
        if (!inq.getState().canReply()) {
            throw new IllegalStateException("Cannot reply in status " + inq.getStatus());
        }
        String sql = "INSERT INTO inquiry_replies (inquiry_id, staff_id, message) VALUES (?, ?, ?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, inquiryId);
            ps.setInt(2, staffId);
            ps.setString(3, message);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if ("OPEN".equals(inq.getStatus())) {
            transition(inquiryId, "START");
        }
        NotificationService.notify(inq.getUserId(), "Reply on " + inq.getReferenceNo(), message);
    }

    public List<Inquiry> listForUser(int userId) {
        return list("SELECT * FROM inquiries WHERE user_id = ? ORDER BY created_at DESC", userId);
    }

    public List<Inquiry> listAll() {
        return list("SELECT * FROM inquiries ORDER BY created_at DESC", null);
    }

    public List<Map<String, Object>> replies(int inquiryId) {
        String sql = "SELECT r.reply_id, r.message, r.created_at, u.full_name FROM inquiry_replies r "
                + "JOIN users u ON u.user_id = r.staff_id WHERE r.inquiry_id = ? ORDER BY r.created_at";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, inquiryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getInt("reply_id"));
                    m.put("message", rs.getString("message"));
                    m.put("staff", rs.getString("full_name"));
                    m.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
                    list.add(m);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    /** Accepts numeric inquiry_id or full reference e.g. INQ-312745CC. */
    public int resolveInquiryId(String referenceOrId) {
        if (referenceOrId == null || referenceOrId.trim().isEmpty()) {
            throw new IllegalArgumentException("Inquiry ID or reference required");
        }
        String raw = referenceOrId.trim();
        if (raw.toUpperCase().startsWith("INQ-")) {
            Inquiry inq = findByReferenceNo(raw.toUpperCase());
            if (inq == null) {
                throw new IllegalArgumentException("Inquiry not found for reference " + raw);
            }
            return inq.getInquiryId();
        }
        try {
            int id = Integer.parseInt(raw);
            if (findById(id) == null) {
                throw new IllegalArgumentException("Inquiry not found (#" + id + "). Use the ID column, not digits from the ref.");
            }
            return id;
        } catch (NumberFormatException e) {
            Inquiry inq = findByReferenceNo(raw);
            if (inq == null) {
                throw new IllegalArgumentException("Inquiry not found");
            }
            return inq.getInquiryId();
        }
    }

    public Inquiry findByReferenceNo(String referenceNo) {
        String sql = "SELECT * FROM inquiries WHERE reference_no = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, referenceNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public Inquiry findById(int id) {
        String sql = "SELECT * FROM inquiries WHERE inquiry_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private void updateStatus(Inquiry inq) {
        String sql = "UPDATE inquiries SET status = ? WHERE inquiry_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, inq.getStatus());
            ps.setInt(2, inq.getInquiryId());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<Inquiry> list(String sql, Integer userId) {
        List<Inquiry> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (userId != null) {
                ps.setInt(1, userId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    private Inquiry map(ResultSet rs) throws Exception {
        Inquiry i = new Inquiry();
        i.setInquiryId(rs.getInt("inquiry_id"));
        i.setReferenceNo(rs.getString("reference_no"));
        i.setUserId(rs.getInt("user_id"));
        i.setCategory(rs.getString("category"));
        i.setSubject(rs.getString("subject"));
        i.setDescription(rs.getString("description"));
        i.setRelatedRef(rs.getString("related_ref"));
        i.setStatus(rs.getString("status"));
        Timestamp c = rs.getTimestamp("created_at");
        if (c != null) {
            i.setCreatedAt(c.toLocalDateTime());
        }
        Timestamp u = rs.getTimestamp("updated_at");
        if (u != null) {
            i.setUpdatedAt(u.toLocalDateTime());
        }
        i.bindState();
        return i;
    }
}
