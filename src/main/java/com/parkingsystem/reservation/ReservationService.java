package com.parkingsystem.reservation;

import com.parkingsystem.livemap.LiveMapModel;
import com.parkingsystem.livemap.Slot;
import com.parkingsystem.livemap.SlotStatus;
import com.parkingsystem.payment.Payment;
import com.parkingsystem.payment.PaymentService;

import java.time.LocalDateTime;

// wires create/cancel/complete and pings observers (map + notifications)
public class ReservationService {

    private final ReservationDAO dao = new ReservationDAO();
    private final ReservationSubject subject = new ReservationSubject();
    private final LiveMapModel mapModel = new LiveMapModel();
    private final PaymentService paymentService = new PaymentService();

    public ReservationService() {
        subject.attach(new MapStatusObserver());
        subject.attach(new NotificationReservationObserver());
    }

    public Reservation create(int userId, int slotId, Integer vehicleId,
                              LocalDateTime start, LocalDateTime end) {
        expireStaleLocks();
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        if (start.isBefore(LocalDateTime.now().minusMinutes(2))) {
            throw new IllegalArgumentException("Start time is in the past");
        }
        Slot slot = mapModel.findSlot(slotId);
        if (slot == null) {
            throw new IllegalArgumentException("Slot not found");
        }
        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new IllegalArgumentException("Slot is not available (" + slot.getStatus() + ")");
        }
        if (dao.hasOverlap(slotId, start, end, null)) {
            throw new IllegalArgumentException("That slot is already booked for this window");
        }

        Reservation r = new Reservation();
        r.setUserId(userId);
        r.setSlotId(slotId);
        r.setVehicleId(vehicleId);
        r.setStartTime(start);
        r.setEndTime(end);
        dao.create(r);
        subject.notifyObservers(r, "CREATED");
        return r;
    }

    public Reservation reschedule(int reservationId, int userId, LocalDateTime start, LocalDateTime end) {
        expireStaleLocks();
        Reservation r = dao.findById(reservationId);
        if (r == null || r.getUserId() != userId) {
            throw new IllegalArgumentException("Reservation not found");
        }
        if (!"PENDING".equals(r.getStatus()) && !"ACTIVE".equals(r.getStatus())) {
            throw new IllegalArgumentException("Cannot reschedule this reservation");
        }
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        if (dao.hasOverlap(r.getSlotId(), start, end, reservationId)) {
            throw new IllegalArgumentException("That slot is already booked for this window");
        }
        if (!dao.updateTimes(reservationId, start, end)) {
            throw new IllegalArgumentException("Could not update times");
        }
        r.setStartTime(start);
        r.setEndTime(end);
        subject.notifyObservers(r, "RESCHEDULED");
        return r;
    }

    public Reservation cancel(int reservationId, int userId) {
        Reservation r = dao.findById(reservationId);
        if (r == null || r.getUserId() != userId) {
            throw new IllegalArgumentException("Reservation not found");
        }
        if ("CANCELLED".equals(r.getStatus()) || "COMPLETED".equals(r.getStatus())) {
            throw new IllegalArgumentException("Cannot cancel this reservation");
        }
        if (!r.getStartTime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Too late to cancel — start time already passed");
        }
        // 1.7 Process Refund when a paid booking is cancelled
        Payment paid = paymentService.dao().findByReservation(reservationId);
        if (paid != null && "PAID".equals(paid.getStatus())) {
            paymentService.refund(paid.getPaymentId(), userId);
        }
        subject.notifyObservers(r, "CANCELLED");
        dao.delete(reservationId);
        r.setStatus("CANCELLED");
        return r;
    }

    public Reservation markActive(int reservationId) {
        Reservation r = dao.findById(reservationId);
        if (r == null) {
            throw new IllegalArgumentException("Reservation not found");
        }
        dao.updateStatus(reservationId, "ACTIVE");
        r.setStatus("ACTIVE");
        return r;
    }

    public Reservation complete(int reservationId) {
        Reservation r = dao.findById(reservationId);
        if (r == null) {
            throw new IllegalArgumentException("Reservation not found");
        }
        dao.updateStatus(reservationId, "COMPLETED");
        r.setStatus("COMPLETED");
        subject.notifyObservers(r, "COMPLETED");
        return r;
    }

    /** Drop unpaid PENDING holds whose lock_until has passed (use case 1.6 / 4a). */
    public int expireStaleLocks() {
        return dao.expireStalePendingLocks();
    }

    public ReservationDAO dao() {
        return dao;
    }
}
