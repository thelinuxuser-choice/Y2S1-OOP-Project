package com.parkingsystem.reservation;

import com.parkingsystem.common.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

// drops a row in notifications so the customer sees booking updates later
public class NotificationReservationObserver implements ReservationObserver {

    @Override
    public void onReservationChanged(Reservation r, String eventType) {
        String title;
        String body;
        switch (eventType) {
            case "CREATED":
                title = "Reservation confirmed";
                body = "Booking #" + r.getReservationId()
                        + " is pending. Token: " + r.getConfirmationToken();
                break;
            case "CANCELLED":
                title = "Reservation cancelled";
                body = "Booking #" + r.getReservationId() + " was cancelled.";
                break;
            case "COMPLETED":
                title = "Reservation completed";
                body = "Booking #" + r.getReservationId() + " is marked completed.";
                break;
            default:
                title = "Reservation update";
                body = "Booking #" + r.getReservationId() + " – " + eventType;
                break;
        }

        String sql = "INSERT INTO notifications (user_id, title, body, is_read) VALUES (?, ?, ?, 0)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, r.getUserId());
            ps.setString(2, title);
            ps.setString(3, body);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("failed to insert notification for user "
                    + r.getUserId(), e);
        }
    }
}
