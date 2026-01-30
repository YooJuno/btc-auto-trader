package com.juno.btctrader.users;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserApiKeyRepository extends JpaRepository<UserApiKey, UUID> {
	List<UserApiKey> findByUserId(UUID userId);
}
