package com.btcautotrader.engine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "trade_decisions",
        indexes = {
                @Index(name = "idx_trade_decisions_executed_at", columnList = "executed_at"),
                @Index(name = "idx_trade_decisions_action_executed_at", columnList = "action,executed_at"),
                @Index(name = "idx_trade_decisions_market_executed_at", columnList = "market,executed_at")
        }
)
public class TradeDecisionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String market;

    @Column(nullable = false)
    private String action;

    @Column(length = 200)
    private String reason;

    @Column(name = "executed_at", nullable = false)
    private OffsetDateTime executedAt;

    @Column
    private String profile;

    @Column(precision = 38, scale = 18)
    private BigDecimal price;

    @Column(precision = 38, scale = 18)
    private BigDecimal quantity;

    @Column(precision = 38, scale = 18)
    private BigDecimal funds;

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "request_status")
    private String requestStatus;

    @Column(name = "ma_short", precision = 38, scale = 18)
    private BigDecimal maShort;

    @Column(name = "ma_long", precision = 38, scale = 18)
    private BigDecimal maLong;

    @Column(precision = 38, scale = 18)
    private BigDecimal rsi;

    @Column(name = "macd_histogram", precision = 38, scale = 18)
    private BigDecimal macdHistogram;

    @Column(name = "breakout_level", precision = 38, scale = 18)
    private BigDecimal breakoutLevel;

    @Column(name = "trailing_high", precision = 38, scale = 18)
    private BigDecimal trailingHigh;

    @Column(name = "ma_long_slope_pct", precision = 38, scale = 18)
    private BigDecimal maLongSlopePct;

    @Column(name = "volatility_pct", precision = 38, scale = 18)
    private BigDecimal volatilityPct;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @PrePersist
    void onCreate() {
        if (executedAt == null) {
            executedAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public OffsetDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(OffsetDateTime executedAt) {
        this.executedAt = executedAt;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getFunds() {
        return funds;
    }

    public void setFunds(BigDecimal funds) {
        this.funds = funds;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getRequestStatus() {
        return requestStatus;
    }

    public void setRequestStatus(String requestStatus) {
        this.requestStatus = requestStatus;
    }

    public BigDecimal getMaShort() {
        return maShort;
    }

    public void setMaShort(BigDecimal maShort) {
        this.maShort = maShort;
    }

    public BigDecimal getMaLong() {
        return maLong;
    }

    public void setMaLong(BigDecimal maLong) {
        this.maLong = maLong;
    }

    public BigDecimal getRsi() {
        return rsi;
    }

    public void setRsi(BigDecimal rsi) {
        this.rsi = rsi;
    }

    public BigDecimal getMacdHistogram() {
        return macdHistogram;
    }

    public void setMacdHistogram(BigDecimal macdHistogram) {
        this.macdHistogram = macdHistogram;
    }

    public BigDecimal getBreakoutLevel() {
        return breakoutLevel;
    }

    public void setBreakoutLevel(BigDecimal breakoutLevel) {
        this.breakoutLevel = breakoutLevel;
    }

    public BigDecimal getTrailingHigh() {
        return trailingHigh;
    }

    public void setTrailingHigh(BigDecimal trailingHigh) {
        this.trailingHigh = trailingHigh;
    }

    public BigDecimal getMaLongSlopePct() {
        return maLongSlopePct;
    }

    public void setMaLongSlopePct(BigDecimal maLongSlopePct) {
        this.maLongSlopePct = maLongSlopePct;
    }

    public BigDecimal getVolatilityPct() {
        return volatilityPct;
    }

    public void setVolatilityPct(BigDecimal volatilityPct) {
        this.volatilityPct = volatilityPct;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
