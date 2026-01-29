package com.hvs.btctrader.paper;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.hvs.btctrader.bot.BotConfig;
import com.hvs.btctrader.config.AppProperties;
import com.hvs.btctrader.strategy.SignalAction;
import com.hvs.btctrader.strategy.StrategyDecision;

@Service
public class PaperTradingService {
	private final Map<String, PaperAccount> accounts = new ConcurrentHashMap<>();
	private final AppProperties properties;
	private final PaperPerformanceService performanceService;

	public PaperTradingService(AppProperties properties, PaperPerformanceService performanceService) {
		this.properties = properties;
		this.performanceService = performanceService;
	}

	public PaperAccount accountFor(String userId) {
		return accounts.computeIfAbsent(userId, id -> new PaperAccount(id, properties.getPaper().getInitialCash()));
	}

	public PaperSummary summaryFor(String userId) {
		PaperAccount account = accountFor(userId);
		double equity = computeEquity(account);
		double unrealized = computeUnrealized(account);
		List<PaperPositionView> positions = new ArrayList<>();
		for (PaperPosition position : account.getPositions().values()) {
			positions.add(PaperPositionView.from(position));
		}
		performanceService.record(userId, equity);
		return new PaperSummary(account.getCashBalance(), equity, account.getRealizedPnl(), unrealized, positions);
	}

	public void recordEquity(String userId) {
		PaperAccount account = accountFor(userId);
		performanceService.record(userId, computeEquity(account));
	}

	public PaperPerformanceResponse performance(String userId, int days, int weeks) {
		return performanceService.build(userId, days, weeks);
	}

	public PaperSummary reset(String userId, double initialCash) {
		PaperAccount account = accountFor(userId);
		account.reset(initialCash);
		return summaryFor(userId);
	}

	public void applySignal(BotConfig config, String market, double price, StrategyDecision decision) {
		PaperAccount account = accountFor(config.getOwner().getId().toString());
		refreshAnchors(account);
		double equity = computeEquity(account);
		double dailyDrawdown = drawdownPct(account.getDailyStartEquity(), equity);
		double weeklyDrawdown = drawdownPct(account.getWeeklyStartEquity(), equity);

		boolean allowBuy = dailyDrawdown <= config.getMaxDailyDrawdownPct().doubleValue()
				&& weeklyDrawdown <= config.getMaxWeeklyDrawdownPct().doubleValue();

		if (decision.action() == SignalAction.BUY) {
			if (!allowBuy) {
				return;
			}
			buy(account, market, price, decision.riskPerTradePct(), config.getMaxPositions());
		} else if (decision.action() == SignalAction.SELL) {
			sell(account, market, price);
		}
	}

	public void updateLastPrice(String userId, String market, double price) {
		PaperAccount account = accountFor(userId);
		PaperPosition position = account.getPositions().get(market);
		if (position != null) {
			position.setLastPrice(price);
		}
	}

	private void buy(PaperAccount account, String market, double price, double riskPerTradePct, int maxPositions) {
		if (account.getPositions().containsKey(market)) {
			return;
		}
		if (account.getPositions().size() >= maxPositions) {
			return;
		}
		double allocation = account.getCashBalance() * (riskPerTradePct / 100.0);
		if (allocation <= 0.0 || allocation > account.getCashBalance()) {
			return;
		}
		double quantity = allocation / price;
		account.setCashBalance(account.getCashBalance() - allocation);
		account.getPositions().put(market, new PaperPosition(market, quantity, price, price));
	}

	private void sell(PaperAccount account, String market, double price) {
		PaperPosition position = account.getPositions().remove(market);
		if (position == null) {
			return;
		}
		double proceeds = position.getQuantity() * price;
		double pnl = (price - position.getEntryPrice()) * position.getQuantity();
		account.setCashBalance(account.getCashBalance() + proceeds);
		account.addRealizedPnl(pnl);
	}

	private void refreshAnchors(PaperAccount account) {
		LocalDate today = LocalDate.now();
		if (!today.equals(account.getDailyAnchorDate())) {
			account.setDailyAnchorDate(today);
			account.setDailyStartEquity(computeEquity(account));
		}
		LocalDate weekStart = today.with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1);
		if (!weekStart.equals(account.getWeeklyAnchorDate())) {
			account.setWeeklyAnchorDate(weekStart);
			account.setWeeklyStartEquity(computeEquity(account));
		}
	}

	private double computeEquity(PaperAccount account) {
		double equity = account.getCashBalance();
		for (PaperPosition position : account.getPositions().values()) {
			equity += position.getQuantity() * position.getLastPrice();
		}
		return equity;
	}

	private double computeUnrealized(PaperAccount account) {
		double pnl = 0.0;
		for (PaperPosition position : account.getPositions().values()) {
			pnl += position.getUnrealizedPnl();
		}
		return pnl;
	}

	private double drawdownPct(double startEquity, double equity) {
		if (startEquity <= 0.0) {
			return 0.0;
		}
		return Math.max(0.0, (startEquity - equity) / startEquity * 100.0);
	}
}
