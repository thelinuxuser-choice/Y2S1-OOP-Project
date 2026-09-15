package com.parkingsystem.payment;

import java.math.BigDecimal;
import java.util.List;

public class QuoteResult {

    private BigDecimal baseAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private List<String> breakdown;
    private String strategiesUsed;

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(BigDecimal baseAmount) {
        this.baseAmount = baseAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getFinalAmount() {
        return finalAmount;
    }

    public void setFinalAmount(BigDecimal finalAmount) {
        this.finalAmount = finalAmount;
    }

    public List<String> getBreakdown() {
        return breakdown;
    }

    public void setBreakdown(List<String> breakdown) {
        this.breakdown = breakdown;
    }

    public String getStrategiesUsed() {
        return strategiesUsed;
    }

    public void setStrategiesUsed(String strategiesUsed) {
        this.strategiesUsed = strategiesUsed;
    }
}
