package com.btcautotrader.portfolio;

import com.btcautotrader.auth.TradingAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {
    private final PortfolioService portfolioService;
    private final TradingAccessService tradingAccessService;

    public PortfolioController(
            PortfolioService portfolioService,
            TradingAccessService tradingAccessService
    ) {
        this.portfolioService = portfolioService;
        this.tradingAccessService = tradingAccessService;
    }

    @GetMapping("/summary")
    public ResponseEntity<PortfolioSummary> getSummary(Authentication authentication) {
        tradingAccessService.requireTenantReadAllowed(authentication);
        return ResponseEntity.ok(portfolioService.getSummary());
    }
}
