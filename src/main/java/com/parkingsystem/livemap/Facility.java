package com.parkingsystem.livemap;

import java.io.Serializable;

// slim POJO for map header – livemap / UALR
public class Facility implements Serializable {

    private int facilityId;
    private String name;
    private String location;
    private boolean active;

    public int getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(int facilityId) {
        this.facilityId = facilityId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
