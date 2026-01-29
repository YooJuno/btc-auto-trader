package com.hvs.btctrader.bot;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hvs.btctrader.auth.JwtUser;
import com.hvs.btctrader.enums.MarketType;
import com.hvs.btctrader.enums.RiskPreset;
import com.hvs.btctrader.enums.SelectionMode;
import com.hvs.btctrader.enums.StrategyMode;
import com.hvs.btctrader.users.User;
import com.hvs.btctrader.users.UserRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/bot-configs")
public class BotConfigController {
	private final BotConfigRepository botConfigRepository;
	private final UserRepository userRepository;

	public BotConfigController(BotConfigRepository botConfigRepository, UserRepository userRepository) {
		this.botConfigRepository = botConfigRepository;
		this.userRepository = userRepository;
	}

	@GetMapping("/defaults")
	public BotDefaultsResponse defaults() {
		return new BotDefaultsResponse(
				MarketType.KRW,
				SelectionMode.AUTO,
				StrategyMode.AUTO,
				RiskPreset.STANDARD,
				3,
				BigDecimal.valueOf(3.0),
				BigDecimal.valueOf(8.0),
				5,
				Arrays.asList(MarketType.values()),
				Arrays.asList(SelectionMode.values()),
				Arrays.asList(StrategyMode.values()),
				Arrays.asList(RiskPreset.values())
		);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public BotConfigResponse create(@AuthenticationPrincipal JwtUser jwtUser,
			@Valid @RequestBody BotConfigRequest request) {
		User owner = userRepository.findById(UUID.fromString(jwtUser.userId()))
				.orElseThrow(() -> new IllegalStateException("User not found."));

		BotConfig config = new BotConfig();
		config.setTenant(owner.getTenant());
		config.setOwner(owner);
		config.setName(request.getName());
		config.setBaseMarket(request.getBaseMarket());
		config.setSelectionMode(request.getSelectionMode());
		config.setStrategyMode(request.getStrategyMode());
		config.setRiskPreset(request.getRiskPreset());
		config.setMaxPositions(request.getMaxPositions());
		config.setMaxDailyDrawdownPct(request.getMaxDailyDrawdownPct());
		config.setMaxWeeklyDrawdownPct(request.getMaxWeeklyDrawdownPct());
		config.setAutoPickTopN(request.getAutoPickTopN());
		config.setManualMarkets(request.getManualMarkets());

		BotConfig saved = botConfigRepository.save(config);
		return BotConfigResponse.from(saved);
	}

	@GetMapping("/active")
	public BotConfigResponse active(@AuthenticationPrincipal JwtUser jwtUser) {
		return botConfigRepository.findFirstByOwnerIdOrderByCreatedAtDesc(UUID.fromString(jwtUser.userId()))
				.map(BotConfigResponse::from)
				.orElse(null);
	}
}
