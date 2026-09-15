package com.parkingsystem.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

// 10 points = LKR 1 off, capped at 30% of the bill
public class LoyaltyRedemptionStrategy implements PricingStrategy {

    @Override
    public String key() {
        return "LOYALTY_REDEEM";
    }

    @Override
    public BigDecimal apply(BigDecimal currentAmount, PricingContext ctx) {
        int pts = ctx.getLoyaltyPointsToRedeem();
        if (pts <= 0) {
            return currentAmount;
        }
        BigDecimal discount = new BigDecimal(pts).divide(new BigDecimal("10"), 2, RoundingMode.HALF_UP);
        BigDecimal max = currentAmount.multiply(new BigDecimal("0.30")).setScale(2, RoundingMode.HALF_UP);
        if (discount.compareTo(max) > 0) {
            discount = max;
        }
        BigDecimal result = currentAmount.subtract(discount);
        return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO.setScale(2) : result.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String description() {
        return "Loyalty points redemption";
    }
}
