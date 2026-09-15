package com.parkingsystem.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;

// peak band 07:00–09:30 and 16:30–19:00 multiplies base
public class PeakHourStrategy implements PricingStrategy {

    private final BigDecimal multiplier;

    public PeakHourStrategy(BigDecimal multiplier) {
        this.multiplier = multiplier == null ? new BigDecimal("1.50") : multiplier;
    }

    @Override
    public String key() {
        return "PEAK_HOUR";
    }

    @Override
    public BigDecimal apply(BigDecimal currentAmount, PricingContext ctx) {
        LocalTime t = ctx.getStartTime().toLocalTime();
        boolean peak = (!t.isBefore(LocalTime.of(7, 0)) && t.isBefore(LocalTime.of(9, 30)))
                || (!t.isBefore(LocalTime.of(16, 30)) && t.isBefore(LocalTime.of(19, 0)));
        if (peak) {
            return currentAmount.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        }
        return currentAmount;
    }

    @Override
    public String description() {
        return "Peak hour x" + multiplier;
    }
}
