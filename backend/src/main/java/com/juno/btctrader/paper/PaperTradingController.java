package com.juno.btctrader.paper;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.juno.btctrader.auth.JwtUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/paper")
public class PaperTradingController {
	private final PaperTradingService paperTradingService;

	public PaperTradingController(PaperTradingService paperTradingService) {
		this.paperTradingService = paperTradingService;
	}

	@GetMapping("/summary")
	public PaperSummary summary(@AuthenticationPrincipal JwtUser jwtUser) {
		return paperTradingService.summaryFor(jwtUser.userId());
	}

	@GetMapping("/performance")
	public PaperPerformanceResponse performance(@AuthenticationPrincipal JwtUser jwtUser,
			@RequestParam(defaultValue = "7") int days,
			@RequestParam(defaultValue = "4") int weeks) {
		return paperTradingService.performance(jwtUser.userId(), days, weeks);
	}

	@PostMapping("/reset")
	public PaperSummary reset(@AuthenticationPrincipal JwtUser jwtUser, @Valid @RequestBody PaperResetRequest request) {
		return paperTradingService.reset(jwtUser.userId(), request.getInitialCash());
	}
}
