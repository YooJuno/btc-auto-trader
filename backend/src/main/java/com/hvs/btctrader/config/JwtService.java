package com.hvs.btctrader.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.hvs.btctrader.auth.JwtUser;

@Service
public class JwtService {
	private final AppProperties properties;
	private final Algorithm algorithm;
	private final JWTVerifier verifier;

	public JwtService(AppProperties properties) {
		this.properties = properties;
		this.algorithm = Algorithm.HMAC256(properties.getJwt().getSecret());
		this.verifier = JWT.require(algorithm)
				.withIssuer(properties.getJwt().getIssuer())
				.build();
	}

	public String generateToken(JwtUser user) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(properties.getJwt().getTtlMinutes(), ChronoUnit.MINUTES);
		return JWT.create()
				.withIssuer(properties.getJwt().getIssuer())
				.withIssuedAt(now)
				.withExpiresAt(expiresAt)
				.withSubject(user.userId())
				.withClaim("tenantId", user.tenantId())
				.withClaim("email", user.email())
				.withClaim("role", user.role())
				.sign(algorithm);
	}

	public JwtUser parseToken(String token) {
		try {
			DecodedJWT decoded = verifier.verify(token);
			String userId = decoded.getSubject();
			String tenantId = decoded.getClaim("tenantId").asString();
			String email = decoded.getClaim("email").asString();
			String role = decoded.getClaim("role").asString();
			return new JwtUser(userId, tenantId, email, role);
		} catch (JWTVerificationException ex) {
			return null;
		}
	}
}
