package com.hvs.btctrader.paper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

@Service
public class PaperPerformanceService {
	private final ConcurrentMap<String, NavigableMap<LocalDate, Double>> dailyEquity = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, NavigableMap<LocalDate, Double>> weeklyEquity = new ConcurrentHashMap<>();

	public void record(String userId, double equity) {
		LocalDate today = LocalDate.now();
		LocalDate weekStart = today.with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1);
		dailyEquity.computeIfAbsent(userId, key -> java.util.Collections.synchronizedNavigableMap(new TreeMap<>()))
				.put(today, equity);
		weeklyEquity.computeIfAbsent(userId, key -> java.util.Collections.synchronizedNavigableMap(new TreeMap<>()))
				.put(weekStart, equity);
	}

	public PaperPerformanceResponse build(String userId, int days, int weeks) {
		NavigableMap<LocalDate, Double> daily = dailyEquity.getOrDefault(userId, new TreeMap<>());
		NavigableMap<LocalDate, Double> weekly = weeklyEquity.getOrDefault(userId, new TreeMap<>());

		List<PaperPerformancePoint> dailyPoints = buildPoints(daily, days, DateTimeFormatter.ofPattern("MM-dd"));
		List<PaperPerformancePoint> weeklyPoints = buildPoints(weekly, weeks, DateTimeFormatter.ofPattern("MM-dd"));

		double totalReturn = calcTotalReturn(dailyPoints);
		double maxDrawdown = calcMaxDrawdown(dailyPoints);

		return new PaperPerformanceResponse(totalReturn, maxDrawdown, dailyPoints, weeklyPoints);
	}

	private List<PaperPerformancePoint> buildPoints(NavigableMap<LocalDate, Double> map, int limit,
			DateTimeFormatter formatter) {
		if (map.isEmpty() || limit <= 0) {
			return List.of();
		}
		List<LocalDate> dates = new ArrayList<>(map.navigableKeySet());
		int startIndex = Math.max(0, dates.size() - limit);
		List<PaperPerformancePoint> points = new ArrayList<>();
		double prev = 0.0;
		for (int i = startIndex; i < dates.size(); i++) {
			LocalDate date = dates.get(i);
			double equity = map.get(date);
			double returnPct = prev > 0.0 ? (equity - prev) / prev * 100.0 : 0.0;
			points.add(new PaperPerformancePoint(date.format(formatter), equity, returnPct));
			prev = equity;
		}
		return points;
	}

	private double calcTotalReturn(List<PaperPerformancePoint> points) {
		if (points.size() < 2) {
			return 0.0;
		}
		double start = points.get(0).equity();
		double end = points.get(points.size() - 1).equity();
		return start > 0.0 ? (end - start) / start * 100.0 : 0.0;
	}

	private double calcMaxDrawdown(List<PaperPerformancePoint> points) {
		double peak = 0.0;
		double maxDrawdown = 0.0;
		for (PaperPerformancePoint point : points) {
			double equity = point.equity();
			if (equity > peak) {
				peak = equity;
			}
			if (peak > 0.0) {
				double drawdown = (peak - equity) / peak * 100.0;
				if (drawdown > maxDrawdown) {
					maxDrawdown = drawdown;
				}
			}
		}
		return maxDrawdown;
	}
}
