package com.btcautotrader.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByProviderAndProviderUserId(String provider, String providerUserId);

    Optional<UserEntity> findFirstByEmailIgnoreCase(String email);

    List<UserEntity> findAllByTenantDatabaseOrderByIdAsc(String tenantDatabase);

    List<UserEntity> findAllByOrderByLastLoginAtDesc();
}
