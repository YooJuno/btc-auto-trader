package com.juno.btctrader.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.juno.btctrader.config.AppProperties;

public class PaperTradingServiceTest {

    @Test
    public void summaryFor_returnsInitialCashAndEquity() {
        AppProperties props = new AppProperties();
        // create a PaperPerformanceService stub that does nothing for record()
        PaperPerformanceService perf = new PaperPerformanceService(null) {
            @Override
            public void record(String userId, double equity) {
                // noop for test
            }
        };

        // use a real PaperStreamService (no external dependencies) for test
        PaperStreamService streamService = new PaperStreamService();

        PaperTradingService svc = new PaperTradingService(props, perf, streamService);
        String userId = "00000000-0000-0000-0000-000000000001";

        PaperSummary summary = svc.summaryFor(userId);

        assertEquals(props.getPaper().getInitialCash(), summary.cashBalance(), 0.0001);
        assertEquals(props.getPaper().getInitialCash(), summary.equity(), 0.0001);
        assertEquals(0, summary.positions().size());
    }

    @Test
    public void reset_setsInitialCash() {
        AppProperties props = new AppProperties();
        PaperPerformanceService perf = new PaperPerformanceService(null) {
            @Override
            public void record(String userId, double equity) {
                // noop
            }
        };

        PaperTradingService svc = new PaperTradingService(props, perf);
        String userId = "00000000-0000-0000-0000-000000000002";

        double newInitial = 12345.67;
        PaperSummary summary = svc.reset(userId, newInitial);

        assertEquals(newInitial, summary.cashBalance(), 0.0001);
        assertEquals(newInitial, summary.equity(), 0.0001);
    }
}