package com.hvs.btctrader.auth;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hvs.btctrader.config.JwtService;
import com.hvs.btctrader.enums.Role;
import com.hvs.btctrader.tenants.Tenant;
import com.hvs.btctrader.tenants.TenantRepository;
import com.hvs.btctrader.users.User;
import com.hvs.btctrader.users.UserRepository;

@Service
public class AuthService {
	private final TenantRepository tenantRepository;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(TenantRepository tenantRepository, UserRepository userRepository,
			PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.tenantRepository = tenantRepository;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.getEmail());
		userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
			throw new IllegalStateException("Email already registered.");
		});
		tenantRepository.findByNameIgnoreCase(request.getTenantName()).ifPresent(existing -> {
			throw new IllegalStateException("Tenant name already exists.");
		});

		Tenant tenant = new Tenant();
		tenant.setName(request.getTenantName().trim());
		tenantRepository.save(tenant);

		User user = new User();
		user.setTenant(tenant);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
		user.setRole(Role.ADMIN);
		userRepository.save(user);

		return buildAuthResponse(user);
	}

	public AuthResponse login(LoginRequest request) {
		String email = normalizeEmail(request.getEmail());
		User user = userRepository.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new IllegalStateException("Invalid credentials."));
		if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
			throw new IllegalStateException("Invalid credentials.");
		}
		return buildAuthResponse(user);
	}

	private AuthResponse buildAuthResponse(User user) {
		JwtUser jwtUser = new JwtUser(
				user.getId().toString(),
				user.getTenant().getId().toString(),
				user.getEmail(),
				user.getRole().name()
		);
		String token = jwtService.generateToken(jwtUser);
		UserProfileResponse profile = new UserProfileResponse(
				user.getId(),
				user.getTenant().getId(),
				user.getTenant().getName(),
				user.getEmail(),
				user.getRole()
		);
		return new AuthResponse(token, profile);
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
