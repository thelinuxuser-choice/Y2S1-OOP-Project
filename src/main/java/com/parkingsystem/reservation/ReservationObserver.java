package com.parkingsystem.reservation;

// Observer hook – map / notifications / whoever cares about booking changes
public interface ReservationObserver {

    void onReservationChanged(Reservation r, String eventType);
}
