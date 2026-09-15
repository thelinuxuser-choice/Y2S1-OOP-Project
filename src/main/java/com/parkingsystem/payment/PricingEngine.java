package com.parkingsystem.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// payment module – MVNS – runs strategies in order at quote time
public class PricingEngine {

    private final List<PricingStrategy> strategies = new ArrayList<>();

    public PricingEngine() {
        strategies.add(new PeakHourStrategy(new BigDecimal("1.50")));
        strategies.add(new EvSlotStrategy(new BigDecimal("1.25")));
        strategies.add(new SlotTypeStrategy());
        strategies.add(new LoyaltyRedemptionStrategy());
    }

    public QuoteResult quote(PricingContext ctx) {
        long minutes = Duration.between(ctx.getStartTime(), ctx.getEndTime()).toMinutes();
        if (minutes < 30) {
            minutes = 30; // minimum half hour charge
        }
        BigDecimal hours = new BigDecimal(minutes).divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP);
        ctx.setHours(hours);

        BigDecimal base = ctx.getBaseRatePerHour().multiply(hours).setScale(2, RoundingMode.HALF_UP);
        BigDecimal running = base;
        List<String> applied = new ArrayList<>();
        applied.add("Base " + hours + "h @ LKR " + ctx.getBaseRatePerHour());

        for (PricingStrategy s : strategies) {
            BigDecimal before = running;
            running = s.apply(running, ctx);
            if (before.compareTo(running) != 0) {
                applied.add(s.description() + " → LKR " + running);
            }
        }

        QuoteResult q = new QuoteResult();
        q.setBaseAmount(base);
        q.setFinalAmount(running);
        q.setDiscountAmount(base.subtract(running).max(BigDecimal.ZERO));
        q.setBreakdown(applied);
        q.setStrategiesUsed(String.join(", ", applied));
        return q;
    }
}
