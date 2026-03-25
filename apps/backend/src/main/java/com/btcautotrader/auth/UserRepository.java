package com.btcautotrader.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long>, JpaSpecificationExecutor<UserEntity> {
    Optional<UserEntity> findByProviderAndProviderUserId(String provider, String providerUserId);

    Optional<UserEntity> findFirstByEmailIgnoreCase(String email);

    List<UserEntity> findAllByTenantDatabaseOrderByIdAsc(String tenantDatabase);

    @Query("select distinct u.tenantDatabase from UserEntity u where u.tenantDatabase is not null")
    List<String> findDistinctTenantDatabases();

    List<UserEntity> findAllByOrderByLastLoginAtDesc();
}
