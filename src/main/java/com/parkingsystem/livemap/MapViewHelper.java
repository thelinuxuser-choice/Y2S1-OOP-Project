package com.parkingsystem.livemap;

import java.util.LinkedHashMap;
import java.util.Map;

// view helper – CSS class names the JS map tiles use
public final class MapViewHelper {

    private MapViewHelper() {
    }

    public static String cssClass(SlotStatus status) {
        if (status == null) {
            return "slot-unknown";
        }
        switch (status) {
            case AVAILABLE:
                return "slot-available";
            case RESERVED:
                return "slot-reserved";
            case OCCUPIED:
                return "slot-occupied";
            case MAINTENANCE:
                return "slot-maintenance";
            default:
                return "slot-unknown";
        }
    }

    public static Map<String, String> legend() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("AVAILABLE", "Free");
        m.put("RESERVED", "Held / booked");
        m.put("OCCUPIED", "Car in bay");
        m.put("MAINTENANCE", "Out of service");
        return m;
    }
}
