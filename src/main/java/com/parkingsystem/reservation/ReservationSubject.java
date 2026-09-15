package com.parkingsystem.reservation;

import java.util.ArrayList;
import java.util.List;

// classic Subject – keep listeners and blast them on booking events
public class ReservationSubject {

    private final List<ReservationObserver> observers = new ArrayList<>();

    public void attach(ReservationObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void detach(ReservationObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers(Reservation reservation, String eventType) {
        for (ReservationObserver observer : observers) {
            observer.onReservationChanged(reservation, eventType);
        }
    }
}
