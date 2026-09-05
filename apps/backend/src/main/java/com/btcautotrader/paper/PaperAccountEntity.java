package com.btcautotrader.paper;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A simulated balance, shaped to mirror one row of Upbit's /v1/accounts response.
 *
 * Deliberately the same fields, including fee-exclusive avg_buy_price: the stop-loss compares the live
 * price against avg_buy_price, so if paper mode used a fee-inclusive cost basis every stop would fire at
 * a different level than it will in production and the rehearsal would be worthless.
 */
@Entity
@Table(name = "paper_accounts")
public class PaperAccountEntity {
    @Id
    @Column(name = "currency", length = 20, nullable = false)
    private String currency;

    @Column(name = "balance", precision = 38, scale = 18, nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "locked", precision = 38, scale = 18, nullable = false)
    private BigDecimal locked = BigDecimal.ZERO;

    @Column(name = "avg_buy_price", precision = 38, scale = 18, nullable = false)
    private BigDecimal avgBuyPrice = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public PaperAccountEntity() {
    }

    public PaperAccountEntity(String currency, BigDecimal balance, BigDecimal avgBuyPrice) {
        this.currency = currency;
        this.balance = balance;
        this.avgBuyPrice = avgBuyPrice;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getLocked() {
        return locked;
    }

    public void setLocked(BigDecimal locked) {
        this.locked = locked;
    }

    public BigDecimal getAvgBuyPrice() {
        return avgBuyPrice;
    }

    public void setAvgBuyPrice(BigDecimal avgBuyPrice) {
        this.avgBuyPrice = avgBuyPrice;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
