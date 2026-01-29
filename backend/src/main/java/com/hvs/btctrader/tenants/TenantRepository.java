package com.hvs.btctrader.tenants;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
	Optional<Tenant> findByNameIgnoreCase(String name);
}
