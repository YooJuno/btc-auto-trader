package com.hvs.btctrader.users;

import java.util.UUID;

import com.hvs.btctrader.common.BaseEntity;
import com.hvs.btctrader.enums.ExchangeType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_api_keys")
public class UserApiKey extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ExchangeType exchange = ExchangeType.UPBIT;

	@Column(nullable = false, length = 2048)
	private String accessKeyEncrypted;

	@Column(nullable = false, length = 2048)
	private String secretKeyEncrypted;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public ExchangeType getExchange() {
		return exchange;
	}

	public void setExchange(ExchangeType exchange) {
		this.exchange = exchange;
	}

	public String getAccessKeyEncrypted() {
		return accessKeyEncrypted;
	}

	public void setAccessKeyEncrypted(String accessKeyEncrypted) {
		this.accessKeyEncrypted = accessKeyEncrypted;
	}

	public String getSecretKeyEncrypted() {
		return secretKeyEncrypted;
	}

	public void setSecretKeyEncrypted(String secretKeyEncrypted) {
		this.secretKeyEncrypted = secretKeyEncrypted;
	}
}
