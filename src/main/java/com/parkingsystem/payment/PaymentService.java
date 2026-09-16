package com.parkingsystem.payment;

import com.parkingsystem.common.AuditLogger;
import com.parkingsystem.common.Vehicle;
import com.parkingsystem.common.VehicleDAO;
import com.parkingsystem.livemap.LiveMapModel;
import com.parkingsystem.livemap.Slot;
import com.parkingsystem.loyalty.LoyaltyProgramManager;
import com.parkingsystem.reservation.Reservation;
import com.parkingsystem.reservation.ReservationDAO;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PaymentService {

    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final ReservationDAO reservationDAO = new ReservationDAO();
    private final LiveMapModel mapModel = new LiveMapModel();
    private final VehicleDAO vehicleDAO = new VehicleDAO();
    private final PricingEngine engine = new PricingEngine();

    public QuoteResult quote(int reservationId, int pointsToRedeem) {
        return quote(reservationId, pointsToRedeem, null);
    }

    public QuoteResult quote(int reservationId, int pointsToRedeem, LocalDateTime actualEnd) {
        Reservation r = reservationDAO.findById(reservationId);
        if (r == null) {
            throw new IllegalArgumentException("Reservation not found");
        }
        Slot slot = mapModel.findSlot(r.getSlotId());
        if (slot == null) {
            throw new IllegalArgumentException("Slot missing");
        }
        PricingContext ctx = new PricingContext();
        ctx.setBaseRatePerHour(slot.getBaseRate());
        ctx.setStartTime(r.getStartTime());
        LocalDateTime end = actualEnd != null && actualEnd.isAfter(r.getEndTime()) ? actualEnd : r.getEndTime();
        ctx.setEndTime(end);
        ctx.setSlotType(slot.getSlotType().name());
        if (slot.getRateStrategyKey() != null && !slot.getRateStrategyKey().isEmpty()) {
            ctx.setRateStrategyKey(slot.getRateStrategyKey());
        }
        ctx.setLoyaltyPointsToRedeem(pointsToRedeem);
        if (r.getVehicleId() != null) {
            Vehicle v = vehicleDAO.findById(r.getVehicleId());
            if (v != null) {
                ctx.setVehicleType(v.getVehicleType());
            }
        }
        QuoteResult q = engine.quote(ctx);
        if (actualEnd != null && actualEnd.isAfter(r.getEndTime())) {
            q.getBreakdown().add("Overstay charged to " + actualEnd);
        }
        return q;
    }

    public Payment checkout(int userId, int reservationId, int pointsToRedeem) {
        Reservation r = reservationDAO.findById(reservationId);
        if (r == null || r.getUserId() != userId) {
            throw new IllegalArgumentException("Reservation not found");
        }
        if (!"PENDING".equals(r.getStatus()) && !"ACTIVE".equals(r.getStatus())) {
            throw new IllegalArgumentException("Cannot pay for this reservation");
        }
        Payment existing = paymentDAO.findByReservation(reservationId);
        if (existing != null && "PAID".equals(existing.getStatus())) {
            throw new IllegalArgumentException("Already paid");
        }

        if (pointsToRedeem > 0) {
            LoyaltyProgramManager.getInstance().redeemPoints(userId, pointsToRedeem, "RES-" + reservationId);
        }

        QuoteResult quote = quote(reservationId, pointsToRedeem);
        Payment p = new Payment();
        p.setReservationId(reservationId);
        p.setBaseAmount(quote.getBaseAmount());
        p.setDiscountAmount(quote.getDiscountAmount());
        p.setFinalAmount(quote.getFinalAmount());
        p.setRateStrategy(String.join(" | ", quote.getBreakdown()));
        p.setStatus("PAID");
        p.setInvoiceNo("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        p.setPaidAt(LocalDateTime.now());
        int id = paymentDAO.insert(p);
        p.setPaymentId(id);

        reservationDAO.updateStatus(reservationId, "ACTIVE");

        int per = LoyaltyProgramManager.getInstance().earnLkrPerPoint();
        int earned = per <= 0 ? 0 : quote.getFinalAmount().intValue() / per;
        if (earned > 0) {
            LoyaltyProgramManager.getInstance().earnPoints(userId, earned, "PAY-" + id);
        }

        AuditLogger.log(userId, "PAYMENT_PAID", "invoice=" + p.getInvoiceNo() + " amount=" + p.getFinalAmount());
        return p;
    }

    /** 7.3 Charge overstay fee when exit is after booked end. */
    public Map<String, Object> chargeOverstayIfNeeded(Reservation r) {
        Map<String, Object> out = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        out.put("overstay", false);
        if (r == null || !now.isAfter(r.getEndTime())) {
            return out;
        }
        long minutes = Duration.between(r.getEndTime(), now).toMinutes();
        QuoteResult booked = quote(r.getReservationId(), 0, r.getEndTime());
        QuoteResult actual = quote(r.getReservationId(), 0, now);
        BigDecimal extra = actual.getFinalAmount().subtract(booked.getFinalAmount());
        if (extra.compareTo(BigDecimal.ZERO) <= 0) {
            return out;
        }
        Payment p = new Payment();
        p.setReservationId(r.getReservationId());
        p.setBaseAmount(extra);
        p.setDiscountAmount(BigDecimal.ZERO);
        p.setFinalAmount(extra);
        p.setRateStrategy("OVERSTAY +" + minutes + " min");
        p.setStatus("PAID");
        p.setInvoiceNo("OVR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        p.setPaidAt(now);
        int id = paymentDAO.insert(p);
        p.setPaymentId(id);
        out.put("overstay", true);
        out.put("minutes", minutes);
        out.put("payment", p);
        AuditLogger.log(r.getUserId(), "PAYMENT_OVERSTAY", "paymentId=" + id + " extra=" + extra);
        return out;
    }

    public Payment refund(int paymentId, int actorId) {
        Payment byId = findPayment(paymentId);
        if (byId == null) {
            throw new IllegalArgumentException("Payment not found");
        }
        if (!paymentDAO.markRefunded(paymentId)) {
            throw new IllegalArgumentException("Refund not allowed");
        }
        byId.setStatus("REFUNDED");

        // reverse loyalty: remove earned points and restore redeemed points
        Reservation r = reservationDAO.findById(byId.getReservationId());
        if (r != null) {
            int per = LoyaltyProgramManager.getInstance().earnLkrPerPoint();
            int earned = per <= 0 ? 0 : byId.getFinalAmount().intValue() / per;
            if (earned > 0) {
                LoyaltyProgramManager.getInstance().adjustPoints(
                        r.getUserId(), -earned, "REFUND-EARN-" + paymentId);
            }
            int redeemedApprox = byId.getDiscountAmount() == null ? 0
                    : byId.getDiscountAmount().multiply(new BigDecimal(
                    LoyaltyProgramManager.getInstance().redeemPointsPerLkr())).intValue();
            if (redeemedApprox > 0) {
                LoyaltyProgramManager.getInstance().adjustPoints(
                        r.getUserId(), redeemedApprox, "REFUND-REDEEM-" + paymentId);
            }
        }

        AuditLogger.log(actorId, "PAYMENT_REFUND", "paymentId=" + paymentId);
        return byId;
    }

    public List<Payment> listForUser(int userId) {
        return paymentDAO.listByUser(userId);
    }

    public Map<String, Object> rateInfo() {
        Map<String, Object> m = new HashMap<>();
        m.put("currency", "LKR");
        m.put("peakWindows", java.util.Arrays.asList("07:00–09:30", "16:30–19:00"));
        m.put("rules", new RateRuleDAO().listActive());
        m.put("strategyKeys", new StrategyKeyDAO().listAll());
        m.put("loyaltyRedeem", "10 points = LKR 1 off (max 30% of bill)");
        return m;
    }

    public StrategyKeyDAO strategyKeys() {
        return new StrategyKeyDAO();
    }

    public Map<String, Object> revenueReport() {
        List<Payment> all = paymentDAO.listAll();
        java.math.BigDecimal totalPaid = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalRefunded = java.math.BigDecimal.ZERO;
        int countPaid = 0;
        int countRefunded = 0;
        for (Payment p : all) {
            if ("PAID".equals(p.getStatus())) {
                totalPaid = totalPaid.add(p.getFinalAmount());
                countPaid++;
            } else if ("REFUNDED".equals(p.getStatus())) {
                totalRefunded = totalRefunded.add(p.getFinalAmount());
                countRefunded++;
            }
        }
        Map<String, Object> m = new HashMap<>();
        m.put("generatedAt", LocalDateTime.now().toString());
        m.put("countPaid", countPaid);
        m.put("countRefunded", countRefunded);
        m.put("totalPaidLkr", totalPaid);
        m.put("totalRefundedLkr", totalRefunded);
        m.put("netRevenueLkr", totalPaid.subtract(totalRefunded));
        m.put("rows", all);
        return m;
    }

    public RateRuleDAO rateRules() {
        return new RateRuleDAO();
    }

    private Payment findPayment(int paymentId) {
        String sql = "SELECT * FROM payments WHERE payment_id = ?";
        try (java.sql.Connection c = com.parkingsystem.common.DBConnection.getConnection();
             java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, paymentId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Payment p = new Payment();
                    p.setPaymentId(rs.getInt("payment_id"));
                    p.setReservationId(rs.getInt("reservation_id"));
                    p.setBaseAmount(rs.getBigDecimal("base_amount"));
                    p.setDiscountAmount(rs.getBigDecimal("discount_amount"));
                    p.setFinalAmount(rs.getBigDecimal("final_amount"));
                    p.setRateStrategy(rs.getString("rate_strategy"));
                    p.setStatus(rs.getString("status"));
                    p.setInvoiceNo(rs.getString("invoice_no"));
                    return p;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public PaymentDAO dao() {
        return paymentDAO;
    }
}
