package com.parkingsystem.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// strategy – swap pricing rules without rewriting checkout
public interface PricingStrategy {

    String key();

    BigDecimal apply(BigDecimal currentAmount, PricingContext ctx);

    String description();
}
