package com.juno.btctrader.bot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BotConfigRepository extends JpaRepository<BotConfig, UUID> {
	List<BotConfig> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
	Optional<BotConfig> findFirstByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
