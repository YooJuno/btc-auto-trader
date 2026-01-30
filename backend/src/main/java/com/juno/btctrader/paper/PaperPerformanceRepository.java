package com.juno.btctrader.paper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperPerformanceRepository extends JpaRepository<PaperPerformanceSnapshot, UUID> {
	Optional<PaperPerformanceSnapshot> findByUserIdAndPeriodTypeAndPeriodDate(UUID userId, PeriodType periodType,
			LocalDate periodDate);

	List<PaperPerformanceSnapshot> findByUserIdAndPeriodTypeOrderByPeriodDateAsc(UUID userId, PeriodType periodType);
}
