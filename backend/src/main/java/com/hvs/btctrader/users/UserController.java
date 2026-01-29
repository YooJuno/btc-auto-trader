package com.hvs.btctrader.users;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hvs.btctrader.auth.JwtUser;
import com.hvs.btctrader.auth.UserProfileResponse;

@RestController
@RequestMapping("/api/users")
public class UserController {
	private final UserRepository userRepository;

	public UserController(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@GetMapping("/me")
	public UserProfileResponse me(@AuthenticationPrincipal JwtUser jwtUser) {
		User user = userRepository.findById(UUID.fromString(jwtUser.userId()))
				.orElseThrow(() -> new IllegalStateException("User not found."));
		return new UserProfileResponse(
				user.getId(),
				user.getTenant().getId(),
				user.getTenant().getName(),
				user.getEmail(),
				user.getRole()
		);
	}
}
