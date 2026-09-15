package com.parkingsystem.livemap;

import com.parkingsystem.common.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

// model layer for the map – pulls floors/slots in one go
public class LiveMapModel {

    public List<Facility> listFacilities() {
        String sql = "SELECT facility_id, name, location, is_active FROM facilities WHERE is_active = 1";
        List<Facility> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Facility f = new Facility();
                f.setFacilityId(rs.getInt("facility_id"));
                f.setName(rs.getString("name"));
                f.setLocation(rs.getString("location"));
                f.setActive(rs.getBoolean("is_active"));
                list.add(f);
            }
        } catch (Exception e) {
            throw new RuntimeException("facilities load failed", e);
        }
        return list;
    }

    public List<Floor> listFloors(int facilityId) {
        String sql = "SELECT floor_id, facility_id, floor_label FROM floors WHERE facility_id = ?";
        List<Floor> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, facilityId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Floor f = new Floor();
                    f.setFloorId(rs.getInt("floor_id"));
                    f.setFacilityId(rs.getInt("facility_id"));
                    f.setFloorLabel(rs.getString("floor_label"));
                    list.add(f);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("floors load failed", e);
        }
        return list;
    }

    public List<Slot> listSlots(int facilityId, String typeFilter) {
        StringBuilder sql = new StringBuilder(
                "SELECT s.slot_id, s.floor_id, fl.floor_label, fl.facility_id, f.name AS facility_name, "
                        + "s.slot_code, s.zone_label, s.slot_type, s.status, s.base_rate, s.pos_row, s.pos_col "
                        + "FROM slots s "
                        + "JOIN floors fl ON fl.floor_id = s.floor_id "
                        + "JOIN facilities f ON f.facility_id = fl.facility_id "
                        + "WHERE fl.facility_id = ?");
        if (typeFilter != null && !typeFilter.isEmpty() && !"ALL".equalsIgnoreCase(typeFilter)) {
            sql.append(" AND s.slot_type = ?");
        }
        sql.append(" ORDER BY fl.floor_id, s.pos_row, s.pos_col");

        List<Slot> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            ps.setInt(1, facilityId);
            if (typeFilter != null && !typeFilter.isEmpty() && !"ALL".equalsIgnoreCase(typeFilter)) {
                ps.setString(2, typeFilter);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapSlot(rs));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("slots load failed", e);
        }
        return list;
    }

    public Slot findSlot(int slotId) {
        String sql = "SELECT s.slot_id, s.floor_id, fl.floor_label, fl.facility_id, f.name AS facility_name, "
                + "s.slot_code, s.zone_label, s.slot_type, s.status, s.base_rate, s.pos_row, s.pos_col "
                + "FROM slots s "
                + "JOIN floors fl ON fl.floor_id = s.floor_id "
                + "JOIN facilities f ON f.facility_id = fl.facility_id "
                + "WHERE s.slot_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, slotId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapSlot(rs);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("slot detail failed", e);
        }
        return null;
    }

    public boolean updateStatus(int slotId, SlotStatus status) {
        String sql = "UPDATE slots SET status = ? WHERE slot_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setInt(2, slotId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            throw new RuntimeException("slot status update failed", e);
        }
    }

    private Slot mapSlot(ResultSet rs) throws Exception {
        Slot s = new Slot();
        s.setSlotId(rs.getInt("slot_id"));
        s.setFloorId(rs.getInt("floor_id"));
        s.setFloorLabel(rs.getString("floor_label"));
        s.setFacilityId(rs.getInt("facility_id"));
        s.setFacilityName(rs.getString("facility_name"));
        s.setSlotCode(rs.getString("slot_code"));
        s.setZoneLabel(rs.getString("zone_label"));
        s.setSlotType(SlotType.valueOf(rs.getString("slot_type")));
        s.setStatus(SlotStatus.valueOf(rs.getString("status")));
        s.setBaseRate(rs.getBigDecimal("base_rate"));
        s.setPosRow(rs.getInt("pos_row"));
        s.setPosCol(rs.getInt("pos_col"));
        return s;
    }
}
