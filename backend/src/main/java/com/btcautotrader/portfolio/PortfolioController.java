package com.btcautotrader.portfolio;

import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Seoul");

    private final PortfolioService portfolioService;
    private final PortfolioPerformanceService portfolioPerformanceService;

    public PortfolioController(
            PortfolioService portfolioService,
            PortfolioPerformanceService portfolioPerformanceService
    ) {
        this.portfolioService = portfolioService;
        this.portfolioPerformanceService = portfolioPerformanceService;
    }

    @GetMapping("/summary")
    public ResponseEntity<PortfolioSummary> getSummary() {
        return ResponseEntity.ok(portfolioService.getSummary());
    }

    @GetMapping("/performance")
    public ResponseEntity<?> getPerformance(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "month", required = false) Integer month
    ) {
        try {
            DateRange range = resolveRange(from, to, year, month);
            return ResponseEntity.ok(portfolioPerformanceService.getPerformance(range.from(), range.to()));
        } catch (IllegalArgumentException ex) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", ex.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    private static DateRange resolveRange(
            LocalDate from,
            LocalDate to,
            Integer year,
            Integer month
    ) {
        if (year != null || month != null) {
            if (from != null || to != null) {
                throw new IllegalArgumentException("year/month mode cannot be combined with from/to");
            }
            if (year == null) {
                throw new IllegalArgumentException("year is required when month is provided");
            }
            if (year < 2009 || year > 2100) {
                throw new IllegalArgumentException("year must be between 2009 and 2100");
            }

            if (month == null) {
                return new DateRange(
                        LocalDate.of(year, 1, 1),
                        LocalDate.of(year, 12, 31)
                );
            }

            if (month < 1 || month > 12) {
                throw new IllegalArgumentException("month must be between 1 and 12");
            }

            LocalDate monthStart = LocalDate.of(year, month, 1);
            return new DateRange(monthStart, monthStart.withDayOfMonth(monthStart.lengthOfMonth()));
        }

        LocalDate today = LocalDate.now(REPORT_ZONE);
        LocalDate safeFrom = from == null ? today.minusDays(29) : from;
        LocalDate safeTo = to == null ? today : to;
        if (safeFrom.isAfter(safeTo)) {
            throw new IllegalArgumentException("from date must be before or equal to to date");
        }
        return new DateRange(safeFrom, safeTo);
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
