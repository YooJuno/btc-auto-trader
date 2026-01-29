package com.hvs.btctrader.tenants;

import java.util.UUID;

import com.hvs.btctrader.common.BaseEntity;
import com.hvs.btctrader.enums.TenantPlan;
import com.hvs.btctrader.enums.TenantStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenants")
public class Tenant extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TenantPlan plan = TenantPlan.FREE;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TenantStatus status = TenantStatus.ACTIVE;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public TenantPlan getPlan() {
		return plan;
	}

	public void setPlan(TenantPlan plan) {
		this.plan = plan;
	}

	public TenantStatus getStatus() {
		return status;
	}

	public void setStatus(TenantStatus status) {
		this.status = status;
	}
}
