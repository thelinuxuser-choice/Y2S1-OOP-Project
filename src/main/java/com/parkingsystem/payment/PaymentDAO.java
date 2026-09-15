package com.parkingsystem.payment;

import com.parkingsystem.common.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PaymentDAO {

    public int insert(Payment p) {
        String sql = "INSERT INTO payments (reservation_id, base_amount, discount_amount, final_amount, "
                + "rate_strategy, status, invoice_no, paid_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getReservationId());
            ps.setBigDecimal(2, p.getBaseAmount());
            ps.setBigDecimal(3, p.getDiscountAmount());
            ps.setBigDecimal(4, p.getFinalAmount());
            ps.setString(5, p.getRateStrategy());
            ps.setString(6, p.getStatus());
            ps.setString(7, p.getInvoiceNo());
            if (p.getPaidAt() == null) {
                ps.setTimestamp(8, null);
            } else {
                ps.setTimestamp(8, Timestamp.valueOf(p.getPaidAt()));
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("payment insert failed", e);
        }
        return -1;
    }

    public Payment findByReservation(int reservationId) {
        String sql = "SELECT * FROM payments WHERE reservation_id = ? ORDER BY payment_id DESC LIMIT 1";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("payment find failed", e);
        }
        return null;
    }

    public boolean markRefunded(int paymentId) {
        String sql = "UPDATE payments SET status = 'REFUNDED' WHERE payment_id = ? AND status = 'PAID'";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, paymentId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            throw new RuntimeException("refund failed", e);
        }
    }

    public List<Payment> listPaid() {
        String sql = "SELECT * FROM payments WHERE status = 'PAID' ORDER BY paid_at DESC";
        List<Payment> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (Exception e) {
            throw new RuntimeException("revenue list failed", e);
        }
        return list;
    }

    public List<Payment> listByUser(int userId) {
        String sql = "SELECT p.* FROM payments p "
                + "JOIN reservations r ON r.reservation_id = p.reservation_id "
                + "WHERE r.user_id = ? ORDER BY p.payment_id DESC";
        List<Payment> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("user payments failed", e);
        }
        return list;
    }

    private Payment map(ResultSet rs) throws Exception {
        Payment p = new Payment();
        p.setPaymentId(rs.getInt("payment_id"));
        p.setReservationId(rs.getInt("reservation_id"));
        p.setBaseAmount(rs.getBigDecimal("base_amount"));
        p.setDiscountAmount(rs.getBigDecimal("discount_amount"));
        p.setFinalAmount(rs.getBigDecimal("final_amount"));
        p.setRateStrategy(rs.getString("rate_strategy"));
        p.setStatus(rs.getString("status"));
        p.setInvoiceNo(rs.getString("invoice_no"));
        Timestamp paid = rs.getTimestamp("paid_at");
        if (paid != null) {
            p.setPaidAt(paid.toLocalDateTime());
        }
        return p;
    }
}
