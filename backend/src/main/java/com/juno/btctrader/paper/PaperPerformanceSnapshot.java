package com.juno.btctrader.paper;

import java.time.LocalDate;
import java.util.UUID;

import com.juno.btctrader.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "paper_performance", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"user_id", "period_type", "period_date"})
})
public class PaperPerformanceSnapshot extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "period_type", nullable = false)
	private PeriodType periodType;

	@Column(name = "period_date", nullable = false)
	private LocalDate periodDate;

	@Column(nullable = false)
	private double equity;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getUserId() {
		return userId;
	}

	public void setUserId(UUID userId) {
		this.userId = userId;
	}

	public PeriodType getPeriodType() {
		return periodType;
	}

	public void setPeriodType(PeriodType periodType) {
		this.periodType = periodType;
	}

	public LocalDate getPeriodDate() {
		return periodDate;
	}

	public void setPeriodDate(LocalDate periodDate) {
		this.periodDate = periodDate;
	}

	public double getEquity() {
		return equity;
	}

	public void setEquity(double equity) {
		this.equity = equity;
	}
}
