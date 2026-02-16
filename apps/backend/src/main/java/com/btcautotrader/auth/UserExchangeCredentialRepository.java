package com.btcautotrader.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserExchangeCredentialRepository extends JpaRepository<UserExchangeCredentialEntity, Long> {
}
