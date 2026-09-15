package com.parkingsystem.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class EvSlotStrategy implements PricingStrategy {

    private final BigDecimal multiplier;

    public EvSlotStrategy(BigDecimal multiplier) {
        this.multiplier = multiplier == null ? new BigDecimal("1.25") : multiplier;
    }

    @Override
    public String key() {
        return "EV_SLOT";
    }

    @Override
    public BigDecimal apply(BigDecimal currentAmount, PricingContext ctx) {
        if ("EV".equalsIgnoreCase(ctx.getSlotType())) {
            return currentAmount.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        }
        return currentAmount;
    }

    @Override
    public String description() {
        return "EV bay x" + multiplier;
    }
}
