package com.btcautotrader.paper;

import com.btcautotrader.upbit.UpbitService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * The single place that decides whether balances come from Upbit or from the simulated book.
 *
 * Both sources return the identical row shape, so callers hold no branch and cannot drift apart —
 * which is what makes a paper run evidence about the live code path rather than about a parallel one.
 */
@Service
public class TradingAccountService {
    private final UpbitService upbitService;
    private final PaperTradingService paperTradingService;

    public TradingAccountService(UpbitService upbitService, PaperTradingService paperTradingService) {
        this.upbitService = upbitService;
        this.paperTradingService = paperTradingService;
    }

    public List<Map<String, Object>> fetchAccounts() {
        return paperTradingService.isPaperMode()
                ? paperTradingService.accountsSnapshot()
                : upbitService.fetchAccounts();
    }

    public boolean isPaperMode() {
        return paperTradingService.isPaperMode();
    }
}
