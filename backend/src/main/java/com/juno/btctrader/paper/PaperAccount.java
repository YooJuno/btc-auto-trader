package com.juno.btctrader.paper;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PaperAccount {
	private final String userId;
	private final Map<String, PaperPosition> positions = new ConcurrentHashMap<>();
	private double cashBalance;
	private double realizedPnl;
	private LocalDate dailyAnchorDate;
	private LocalDate weeklyAnchorDate;
	private double dailyStartEquity;
	private double weeklyStartEquity;

	public PaperAccount(String userId, double initialCash) {
		this.userId = userId;
		this.cashBalance = initialCash;
		LocalDate today = LocalDate.now();
		this.dailyAnchorDate = today;
		this.weeklyAnchorDate = weekStart(today);
		this.dailyStartEquity = initialCash;
		this.weeklyStartEquity = initialCash;
	}

	public String getUserId() {
		return userId;
	}

	public Map<String, PaperPosition> getPositions() {
		return positions;
	}

	public double getCashBalance() {
		return cashBalance;
	}

	public void setCashBalance(double cashBalance) {
		this.cashBalance = cashBalance;
	}

	public double getRealizedPnl() {
		return realizedPnl;
	}

	public void addRealizedPnl(double pnl) {
		this.realizedPnl += pnl;
	}

	public LocalDate getDailyAnchorDate() {
		return dailyAnchorDate;
	}

	public void setDailyAnchorDate(LocalDate dailyAnchorDate) {
		this.dailyAnchorDate = dailyAnchorDate;
	}

	public LocalDate getWeeklyAnchorDate() {
		return weeklyAnchorDate;
	}

	public void setWeeklyAnchorDate(LocalDate weeklyAnchorDate) {
		this.weeklyAnchorDate = weeklyAnchorDate;
	}

	public double getDailyStartEquity() {
		return dailyStartEquity;
	}

	public void setDailyStartEquity(double dailyStartEquity) {
		this.dailyStartEquity = dailyStartEquity;
	}

	public double getWeeklyStartEquity() {
		return weeklyStartEquity;
	}

	public void setWeeklyStartEquity(double weeklyStartEquity) {
		this.weeklyStartEquity = weeklyStartEquity;
	}

	public void reset(double initialCash) {
		positions.clear();
		cashBalance = initialCash;
		realizedPnl = 0.0;
		LocalDate today = LocalDate.now();
		dailyAnchorDate = today;
		weeklyAnchorDate = weekStart(today);
		dailyStartEquity = initialCash;
		weeklyStartEquity = initialCash;
	}

	private LocalDate weekStart(LocalDate date) {
		return date.with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1);
	}
}
