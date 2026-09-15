package com.parkingsystem.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class SlotTypeStrategy implements PricingStrategy {

    @Override
    public String key() {
        return "SLOT_TYPE";
    }

    @Override
    public BigDecimal apply(BigDecimal currentAmount, PricingContext ctx) {
        String type = ctx.getSlotType();
        if ("ACCESSIBLE".equalsIgnoreCase(type)) {
            return currentAmount.multiply(new BigDecimal("0.80")).setScale(2, RoundingMode.HALF_UP);
        }
        if ("COVERED".equalsIgnoreCase(type)) {
            return currentAmount.multiply(new BigDecimal("1.15")).setScale(2, RoundingMode.HALF_UP);
        }
        return currentAmount;
    }

    @Override
    public String description() {
        return "Accessible / covered adjustments";
    }
}
