package com.parkingsystem.reservation;

import com.parkingsystem.common.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

// keeps the live map honest when someone books / drops / finishes a bay
public class MapStatusObserver implements ReservationObserver {

    @Override
    public void onReservationChanged(Reservation r, String eventType) {
        String slotStatus;
        switch (eventType) {
            case "CREATED":
                slotStatus = "RESERVED";
                break;
            case "CANCELLED":
                slotStatus = "AVAILABLE";
                break;
            case "COMPLETED":
                // leave map alone here – gate checkout sets AVAILABLE after exit
                return;
            default:
                return;
        }

        String sql = "UPDATE slots SET status = ? WHERE slot_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, slotStatus);
            ps.setInt(2, r.getSlotId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("couldn't sync slot status for reservation "
                    + r.getReservationId(), e);
        }
    }
}
