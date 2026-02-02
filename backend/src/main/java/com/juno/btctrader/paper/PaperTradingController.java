package com.juno.btctrader.paper;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.juno.btctrader.auth.JwtUser;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/paper")
public class PaperTradingController {
	private final PaperTradingService paperTradingService;
	private final PaperStreamService paperStreamService;
	private final com.juno.btctrader.config.JwtService jwtService;

	public PaperTradingController(PaperTradingService paperTradingService, PaperStreamService paperStreamService, com.juno.btctrader.config.JwtService jwtService) {
		this.paperTradingService = paperTradingService;
		this.paperStreamService = paperStreamService;
		this.jwtService = jwtService;
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

	/**
	 * Server-Sent Events stream for getting live PaperSummary updates.
	 * Accepts token as query parameter for EventSource clients that cannot set headers.
	 */
	@GetMapping("/stream")
	public SseEmitter stream(@AuthenticationPrincipal JwtUser jwtUser, @RequestParam(required = false) String token) {
		JwtUser principal = jwtUser;
		if (principal == null && token != null && !token.isBlank()) {
			principal = jwtService.parseToken(token);
		}
		if (principal == null) {
			throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
		}
		String userId = principal.userId();
		return paperStreamService.subscribe(userId);
	}
}
