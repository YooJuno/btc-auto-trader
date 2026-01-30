package com.juno.btctrader.paper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaperPerformanceService {
	private final PaperPerformanceRepository repository;

	public PaperPerformanceService(PaperPerformanceRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public void record(String userId, double equity) {
		UUID userUuid = UUID.fromString(userId);
		LocalDate today = LocalDate.now();
		LocalDate weekStart = today.with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1);
		upsert(userUuid, PeriodType.DAILY, today, equity);
		upsert(userUuid, PeriodType.WEEKLY, weekStart, equity);
	}

	public PaperPerformanceResponse build(String userId, int days, int weeks) {
		UUID userUuid = UUID.fromString(userId);
		List<PaperPerformanceSnapshot> dailySnapshots =
				repository.findByUserIdAndPeriodTypeOrderByPeriodDateAsc(userUuid, PeriodType.DAILY);
		List<PaperPerformanceSnapshot> weeklySnapshots =
				repository.findByUserIdAndPeriodTypeOrderByPeriodDateAsc(userUuid, PeriodType.WEEKLY);

		List<PaperPerformancePoint> dailyPoints = buildPoints(dailySnapshots, days, DateTimeFormatter.ofPattern("MM-dd"));
		List<PaperPerformancePoint> weeklyPoints = buildPoints(weeklySnapshots, weeks,
				DateTimeFormatter.ofPattern("MM-dd"));

		double totalReturn = calcTotalReturn(dailyPoints);
		double maxDrawdown = calcMaxDrawdown(dailyPoints);

		return new PaperPerformanceResponse(totalReturn, maxDrawdown, dailyPoints, weeklyPoints);
	}

	private void upsert(UUID userId, PeriodType periodType, LocalDate periodDate, double equity) {
		PaperPerformanceSnapshot snapshot = repository
				.findByUserIdAndPeriodTypeAndPeriodDate(userId, periodType, periodDate)
				.orElseGet(PaperPerformanceSnapshot::new);
		snapshot.setUserId(userId);
		snapshot.setPeriodType(periodType);
		snapshot.setPeriodDate(periodDate);
		snapshot.setEquity(equity);
		repository.save(snapshot);
	}

	private List<PaperPerformancePoint> buildPoints(List<PaperPerformanceSnapshot> snapshots, int limit,
			DateTimeFormatter formatter) {
		if (snapshots.isEmpty() || limit <= 0) {
			return List.of();
		}
		int startIndex = Math.max(0, snapshots.size() - limit);
		List<PaperPerformancePoint> points = new ArrayList<>();
		double prev = 0.0;
		for (int i = startIndex; i < snapshots.size(); i++) {
			PaperPerformanceSnapshot snapshot = snapshots.get(i);
			double equity = snapshot.getEquity();
			double returnPct = prev > 0.0 ? (equity - prev) / prev * 100.0 : 0.0;
			points.add(new PaperPerformancePoint(snapshot.getPeriodDate().format(formatter), equity, returnPct));
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
