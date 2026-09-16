package com.parkingsystem.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Applies the rate rule multiplier for the bay's assigned strategy key. */
public class SlotAssignedStrategy implements PricingStrategy {

    private final RateRuleDAO rules = new RateRuleDAO();

    @Override
    public String key() {
        return "SLOT_KEY";
    }

    @Override
    public BigDecimal apply(BigDecimal currentAmount, PricingContext ctx) {
        String slotKey = ctx.getRateStrategyKey();
        if (slotKey == null || slotKey.trim().isEmpty()) {
            return currentAmount;
        }
        if ("PEAK_HOUR".equalsIgnoreCase(slotKey)) {
            return currentAmount;
        }
        BigDecimal mult = rules.activeMultiplier(slotKey.toUpperCase());
        if (mult == null) {
            return currentAmount;
        }
        return currentAmount.multiply(mult).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String description() {
        return "Bay strategy key";
    }
}
