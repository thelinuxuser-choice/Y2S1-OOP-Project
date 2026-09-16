package com.parkingsystem.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PricingContext {

    private BigDecimal baseRatePerHour;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String slotType;
    private String vehicleType;
    private int loyaltyPointsToRedeem;
    private BigDecimal hours;
    /** Optional bay-level pricing key from slots.rate_strategy_key */
    private String rateStrategyKey;

    public BigDecimal getBaseRatePerHour() {
        return baseRatePerHour;
    }

    public void setBaseRatePerHour(BigDecimal baseRatePerHour) {
        this.baseRatePerHour = baseRatePerHour;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getSlotType() {
        return slotType;
    }

    public void setSlotType(String slotType) {
        this.slotType = slotType;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public int getLoyaltyPointsToRedeem() {
        return loyaltyPointsToRedeem;
    }

    public void setLoyaltyPointsToRedeem(int loyaltyPointsToRedeem) {
        this.loyaltyPointsToRedeem = loyaltyPointsToRedeem;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public void setHours(BigDecimal hours) {
        this.hours = hours;
    }

    public String getRateStrategyKey() {
        return rateStrategyKey;
    }

    public void setRateStrategyKey(String rateStrategyKey) {
        this.rateStrategyKey = rateStrategyKey;
    }
}
