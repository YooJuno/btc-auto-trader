package com.btcautotrader.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "portfolio_snapshot_item")
public class PortfolioSnapshotItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_id", nullable = false)
    private Long snapshotId;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal balance;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal locked;

    @Column(name = "avg_buy_price", nullable = false, precision = 38, scale = 18)
    private BigDecimal avgBuyPrice;

    @Column(name = "unit_currency", nullable = false, length = 10)
    private String unitCurrency;

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public Long getSnapshotId() {
        return snapshotId;
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

    public String getUnitCurrency() {
        return unitCurrency;
    }

    public void setUnitCurrency(String unitCurrency) {
        this.unitCurrency = unitCurrency;
    }
}
